package ru.tuvio.tools.dlnaclient;

import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.ui.*;
import java.util.*;

/** Visible-only playback, persistent resume and per-file track choices. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
 private ExoPlayer player;
 private PlayerView playerView;
 private PlaybackPreferences prefs;
 private ResumeStore store;
 private Server server;
 private DmsItem item;
 private long position;
 private boolean playWhenReady=true,completed,ready;
 private int originalDisplayMode;
 private AlertDialog dialog;
 private PlaybackChoiceRow audioRow, captionRow, speedRow;
 private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable saver=new Runnable(){@Override public void run(){savePosition();handler.postDelayed(this,5000);}};
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);setContentView(R.layout.activity_player);
  server=(Server)getIntent().getSerializableExtra("server");item=(DmsItem)getIntent().getSerializableExtra("item");
  if(server==null||item==null||item.url==null||item.url.isEmpty()){Toast.makeText(this,R.string.error_play,Toast.LENGTH_LONG).show();finish();return;}
  store=new ResumeStore(this);prefs=new PlaybackPreferences(this);
  position=state==null?getIntent().getLongExtra("startMs",0):state.getLong("position");
  if(state!=null){playWhenReady=state.getBoolean("playing",true);completed=state.getBoolean("completed");}
  originalDisplayMode=getWindow().getAttributes().preferredDisplayModeId;
  playerView=findViewById(R.id.playerView);
  FineTimeBar bar=playerView.findViewById(androidx.media3.ui.R.id.exo_progress);
  if(bar!=null)bar.setBaseIncrement(prefs.seekMs());
  audioRow = findViewById(R.id.audioRow);
  captionRow = findViewById(R.id.captionRow);
  speedRow = findViewById(R.id.speedRow);
  View rows = findViewById(R.id.playbackRows);
  playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
   findViewById(R.id.playerChrome).setVisibility(visibility);
   rows.setVisibility(visibility);
   SubtitleView subtitles = playerView.getSubtitleView();
   if (subtitles != null) {
    int height = Math.max(playerView.getHeight(), getResources().getDisplayMetrics().heightPixels);
    float padding = visibility == View.VISIBLE ? Math.min(0.6f, Ui.dp(this, 230) / (float) height) : 0.08f;
    subtitles.setBottomPaddingFraction(padding);
   }
  });
  rows.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
   boolean wasInRows = rowForFocus(oldFocus) != null;
   boolean inRows = rowForFocus(newFocus) != null;
   if (wasInRows != inRows && newFocus != null) playerView.setControllerShowTimeoutMs(inRows ? 0 : 5000);
  });
  ((TextView)findViewById(R.id.playingTitle)).setText(item.title);
  playerView.findViewById(R.id.playerMore).setOnClickListener(v -> showOptions());
  playerView.requestFocus();
 }
 @Override protected void onStart(){
  super.onStart();if(isFinishing()||item==null||playerView==null)return;
  DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(15000,60000,1000,2500)
    .setTargetBufferBytes(prefs.bufferMb()*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build();
  player=new ExoPlayer.Builder(this).setRenderersFactory(new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
    .setLoadControl(load).setSeekParameters(SeekParameters.EXACT)
    .setSeekBackIncrementMs(prefs.seekMs()).setSeekForwardIncrementMs(prefs.seekMs()).build();
  player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
  TrackSelectionParameters.Builder selection = player.getTrackSelectionParameters().buildUpon();
  selection.setPreferredAudioLanguage(prefs.language("audio_language", "ru"));
  configureAutomaticSubtitles(selection);
  player.setTrackSelectionParameters(selection.build());
  playerView.setPlayer(player);applyAppearance();
  player.setPlaybackSpeed(speed());
  updatePlaybackRows();
  player.addListener(new Player.Listener(){
   @Override public void onPlaybackStateChanged(int state){
    if(state==Player.STATE_READY){ready=true;if(completed&&player.getCurrentPosition()<player.getDuration()-1000)completed=false;matchFrameRate();}
    if(state==Player.STATE_ENDED){completed=true;store.setWatched(server.udn,item.objectId,true);}
   }
   @Override public void onPlaybackParametersChanged(PlaybackParameters parameters){prefs.set("speed",Float.toString(parameters.speed)); updateSpeedRow();}
   @Override public void onTracksChanged(Tracks tracks){restoreTrack(C.TRACK_TYPE_AUDIO); restoreTrack(C.TRACK_TYPE_TEXT); updateForcedSubtitles(tracks); updatePlaybackRows();}
   @Override public void onVideoSizeChanged(VideoSize size){matchFrameRate();}
   @Override public void onMediaMetadataChanged(MediaMetadata metadata){((TextView)findViewById(R.id.playingTitle)).setText(metadata.title==null?item.title:metadata.title);}
   @Override public void onPlayerError(PlaybackException error){Toast.makeText(PlayerActivity.this,getString(R.string.error_play)+"\n"+error.getErrorCodeName(),Toast.LENGTH_LONG).show();}
  });
  MediaItem.Builder media=new MediaItem.Builder().setUri(item.url);
  List<MediaItem.SubtitleConfiguration> subtitles=new ArrayList<>();
  if(item.subtitles!=null)for(DmsItem.Subtitle sub:item.subtitles){
   MediaItem.SubtitleConfiguration.Builder config=new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url)).setMimeType(sub.mime).setLabel(sub.label);
   if(sub.language!=null&&!sub.language.isEmpty())config.setLanguage(sub.language);
   subtitles.add(config.build());
  }
  media.setSubtitleConfigurations(subtitles);
  player.setMediaItem(media.build());player.seekTo(position);player.prepare();player.setPlayWhenReady(playWhenReady&&!completed);
  handler.postDelayed(saver,5000);
 }
 private void configureAutomaticSubtitles(TrackSelectionParameters.Builder builder) {
  String mode = prefs.string("subtitle_language", "forced");
  boolean forcedOnly = mode.equals("forced");
  builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, mode.equals("off"))
    .setPreferredTextLanguage(forcedOnly || mode.equals("off") ? null : prefs.language("subtitle_language", "forced"))
    .setPreferredTextRoleFlags(0)
    .setIgnoredTextSelectionFlags(forcedOnly ? C.SELECTION_FLAG_DEFAULT : 0)
    .setSelectUndeterminedTextLanguage(!forcedOnly && !mode.equals("off"));
 }
 private float speed(){try{return Math.max(0.5f,Math.min(2f,Float.parseFloat(prefs.string("speed","1.0"))));}catch(Exception e){return 1f;}}
 private void applyAppearance(){
  String aspect=prefs.string("aspect","fit");playerView.setResizeMode(aspect.equals("zoom")?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:aspect.equals("fill")?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT);
  SubtitleView view=playerView.getSubtitleView();if(view==null)return;
  String style=prefs.string("subtitle_style","original");view.setApplyEmbeddedStyles(style.equals("original"));
  view.setApplyEmbeddedFontSizes(!prefs.bool("subtitle_size_custom",false));
  view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP,prefs.subtitleSize());
  view.setStyle(new CaptionStyleCompat(style.equals("yellow")?Color.YELLOW:Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT,CaptionStyleCompat.EDGE_TYPE_OUTLINE,Color.BLACK,Typeface.DEFAULT));
 }
 private void showOptions(){
  if(player==null)return;playerView.showController();
  dialog = showPlaybackPanel(new AlertDialog.Builder(this).setTitle("Параметры воспроизведения")
   .setItems(new String[]{"Масштаб изображения","Размер субтитров","Оформление субтитров","Информация и теги"},(d,which)->{
    switch(which){
     case 0:choose("Масштаб",new String[]{"Вписать целиком","Приблизить с обрезкой","Растянуть"},i->{prefs.set("aspect",new String[]{"fit","zoom","fill"}[i]);applyAppearance();});break;
     case 1:choose("Размер субтитров",new String[]{"Мелкие","Обычные","Крупные","Очень крупные"},i->{prefs.set("subtitle_size",new int[]{18,22,28,34}[i]);prefs.set("subtitle_size_custom",true);applyAppearance();});break;
     case 2:choose("Оформление",new String[]{"Стили из файла","Белый с обводкой","Жёлтый с обводкой"},i->{prefs.set("subtitle_style",new String[]{"original","white","yellow"}[i]);applyAppearance();});break;
     case 3:showInfo();break;
    }
   }));
 }
 private void choose(String title,String[] labels,java.util.function.IntConsumer action){dialog = showPlaybackPanel(new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->action.accept(i)));}
 private String choiceKey(int type){return "track:"+server.udn.length()+":"+server.udn+item.objectId+":"+type;}
 private String fingerprint(Format f){return Objects.toString(f.id,"")+"|"+Objects.toString(f.language,"")+"|"+Objects.toString(f.label,"")+"|"+Objects.toString(f.sampleMimeType,"")+"|"+f.channelCount;}
 private void restoreTrack(int type){
  String saved=prefs.string(choiceKey(type),"");if(saved.isEmpty()||player==null)return;
  TrackSelectionParameters current=player.getTrackSelectionParameters();
  if(saved.equals("off")){
   if(!current.disabledTrackTypes.contains(type))player.setTrackSelectionParameters(current.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,true).build());
   return;
  }
  for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)
   for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)&&fingerprint(group.getTrackFormat(i)).equals(saved)){
    if(!group.isTrackSelected(i)||current.disabledTrackTypes.contains(type))player.setTrackSelectionParameters(current.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,false).addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(),i)).build());
    return;
   }
 }
 private PlaybackChoiceRow rowForFocus(View focus) {
  while (focus != null) {
   if (focus instanceof PlaybackChoiceRow) return (PlaybackChoiceRow) focus;
   focus = focus.getParent() instanceof View ? (View) focus.getParent() : null;
  }
  return null;
 }

 private void updatePlaybackRows() {
  bindTrackRow(C.TRACK_TYPE_AUDIO, audioRow);
  bindTrackRow(C.TRACK_TYPE_TEXT, captionRow);
  updateSpeedRow();
 }

 private void updateSpeedRow() {
  if (player == null) return;
  List<String> labels = new ArrayList<>();
  int selected = -1;
  for (int i = 0; i < SPEEDS.length; i++) {
   labels.add(java.text.NumberFormat.getNumberInstance().format(SPEEDS[i]) + "×");
   if (player.getPlaybackParameters().speed == SPEEDS[i]) selected = i;
  }
  speedRow.bind("Скорость", labels, selected, i -> player.setPlaybackSpeed(SPEEDS[i]));
 }

 private void bindTrackRow(int type, PlaybackChoiceRow row){
  if(player==null)return;
  List<String> labels=new ArrayList<>();List<Tracks.Group> groups=new ArrayList<>();List<Integer> indexes=new ArrayList<>();
  if(type==C.TRACK_TYPE_TEXT)labels.add("Выключены");
  labels.add("Авто");
  int prefix = labels.size();
  int selected = type == C.TRACK_TYPE_TEXT
    && player.getTrackSelectionParameters().disabledTrackTypes.contains(type) ? 0 : prefix - 1;
  for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)
   for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){
    if(group.isTrackSelected(i))selected=labels.size();labels.add(trackLabel(group.getTrackFormat(i),labels.size()-prefix+1));groups.add(group);indexes.add(i);
   }
  row.bind(getString(type == C.TRACK_TYPE_AUDIO ? R.string.player_audio : R.string.player_captions),
    labels, selected, which -> {
    if (player == null) return;
    TrackSelectionParameters.Builder builder=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type);
    if(type==C.TRACK_TYPE_TEXT&&which==0){prefs.set(choiceKey(type),"off");builder.setTrackTypeDisabled(type,true);}
    else if(which<prefix){
     prefs.remove(choiceKey(type));
     if (type == C.TRACK_TYPE_TEXT) configureAutomaticSubtitles(builder);
     else builder.setPreferredAudioLanguage(prefs.language("audio_language", "ru"));
    }else{
     Tracks.Group group=groups.get(which-prefix);int index=indexes.get(which-prefix);
     prefs.set(choiceKey(type),fingerprint(group.getTrackFormat(index)));
     builder.setTrackTypeDisabled(type,false).addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(),index));
    }
    player.setTrackSelectionParameters(builder.build());
    if (type == C.TRACK_TYPE_TEXT) updateForcedSubtitles(player.getCurrentTracks());
    updatePlaybackRows();
   });
 }
 private void updateForcedSubtitles(Tracks tracks) {
  if (!prefs.string("subtitle_language", "forced").equals("forced")
    || !prefs.string(choiceKey(C.TRACK_TYPE_TEXT), "").isEmpty()) return;
  String audio = "";
  for (Tracks.Group group : tracks.getGroups()) {
   if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
   for (int i = 0; i < group.length; i++) {
    if (group.isTrackSelected(i)) audio = ForcedSubtitlePolicy.language(group.getTrackFormat(i).language);
   }
  }
  int best = 0;
  TrackSelectionOverride forced = null;
  for (Tracks.Group group : tracks.getGroups()) {
   if (group.getType() != C.TRACK_TYPE_TEXT) continue;
   for (int i = 0; i < group.length; i++) {
    if (!group.isTrackSupported(i)) continue;
    Format format = group.getTrackFormat(i);
    int score = ForcedSubtitlePolicy.score(audio, format.language, format.label,
      (format.selectionFlags & C.SELECTION_FLAG_FORCED) != 0);
    if (score > best) {
     best = score;
     forced = new TrackSelectionOverride(group.getMediaTrackGroup(), i);
    }
   }
  }
  TrackSelectionParameters current = player.getTrackSelectionParameters();
  TrackSelectionParameters.Builder next = current.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT);
  if (forced != null) next.addOverride(forced);
  TrackSelectionParameters updated = next.build();
  if (!updated.equals(current)) player.setTrackSelectionParameters(updated);
 }

 private AlertDialog showPlaybackPanel(AlertDialog.Builder builder) {
  AlertDialog result = builder.show();
  Window window = result.getWindow();
  if (window != null) {
   window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
   window.setLayout(Math.min(Ui.dp(this, 560),
     getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48)), ViewGroup.LayoutParams.WRAP_CONTENT);
   WindowManager.LayoutParams layout = window.getAttributes();
   layout.y = Ui.dp(this, 60);
   window.setAttributes(layout);
   window.setDimAmount(0.22f);
  }
  return result;
 }
 private String trackLabel(Format f,int ordinal){
  List<String> parts=new ArrayList<>();parts.add(f.label==null||f.label.isEmpty()?"Дорожка "+ordinal:f.label);
  if(f.language!=null&&!f.language.equals("und"))parts.add(Locale.forLanguageTag(f.language).getDisplayLanguage());
  if(f.sampleMimeType!=null)parts.add(f.sampleMimeType.replace("audio/","").replace("application/","").replace("text/",""));
  if (((f.selectionFlags & C.SELECTION_FLAG_FORCED) != 0 || ForcedSubtitlePolicy.hasForcedLabel(f.label))) parts.add("Форсированные");
  if(f.channelCount>0)parts.add(f.channelCount+" кан.");return String.join(" · ",parts);
 }
 private void showInfo(){
  if(player==null)return;StringBuilder text=new StringBuilder();MediaMetadata m=player.getMediaMetadata();
  line(text,"Название",m.title==null?item.title:m.title);line(text,"Файл",item.title);
  line(text,"Исполнитель",m.artist==null?item.artist:m.artist);line(text,"Альбом",m.albumTitle==null?item.album:m.albumTitle);
  line(text,"Жанр",m.genre==null?item.genre:m.genre);line(text,"Год",m.releaseYear==null?m.recordingYear:m.releaseYear);
  line(text,"Описание",m.description==null?item.description:m.description);line(text,"Дата",item.date);
  line(text,"Контейнер",item.mimeType);if(item.size>0)line(text,"Размер",android.text.format.Formatter.formatShortFileSize(this,item.size));
  if(player.getDuration()>0)line(text,"Длительность",Ui.time(player.getDuration()));
  Format video=player.getVideoFormat(),audio=player.getAudioFormat();
  if(video!=null){line(text,"Видео",video.sampleMimeType+" · "+video.width+"×"+video.height);if(video.frameRate>0)line(text,"Частота кадров",String.format(Locale.getDefault(),"%.3f",video.frameRate));line(text,"Видеокодек",video.codecs);}
  if(audio!=null){line(text,"Аудио",trackLabel(audio,1));if(audio.sampleRate>0)line(text,"Частота звука",audio.sampleRate+" Гц");line(text,"Аудиокодек",audio.codecs);}
  int count=0;for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_TEXT)count+=group.length;
  line(text,"Дорожек субтитров",count);line(text,"Плеер","Media3 · аппаратные возможности Android");
  TextView view=new TextView(this);view.setText(text);view.setTextSize(14);view.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,12));view.setTextColor(0xffe4e9f1);
  ScrollView scroll=new ScrollView(this);scroll.addView(view);
  dialog=new AlertDialog.Builder(this).setTitle("Информация и теги").setView(scroll).setPositiveButton("Закрыть",null).show();
 }
 private void line(StringBuilder b,String key,Object value){if(value!=null&&!value.toString().trim().isEmpty())b.append(key).append(": ").append(value).append("\n\n");}
 private void matchFrameRate(){
  if(player==null||!prefs.bool("frame_match",false))return;Format format=player.getVideoFormat();if(format==null||format.frameRate<=0)return;
  Display display=getWindowManager().getDefaultDisplay();Display.Mode current=display.getMode();List<Display.Mode> modes=new ArrayList<>();int currentIndex=-1;
  for(Display.Mode mode:display.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight()){
   if(mode.getModeId()==current.getModeId())currentIndex=modes.size();modes.add(mode);
  }
  float[] rates=new float[modes.size()];for(int i=0;i<rates.length;i++)rates[i]=modes.get(i).getRefreshRate();
  int selected=FrameRateMatcher.choose(format.frameRate,rates,currentIndex);
  if(selected>=0&&selected!=currentIndex){WindowManager.LayoutParams params=getWindow().getAttributes();params.preferredDisplayModeId=modes.get(selected).getModeId();getWindow().setAttributes(params);}
 }
 private void savePosition(){if(player!=null&&ready&&!completed)store.save(server.udn,item.objectId,item.title,item.url,player.getCurrentPosition(),player.getDuration());}
 @Override protected void onPause(){if(player!=null){savePosition();playWhenReady=player.getPlayWhenReady();player.pause();}super.onPause();}
 @Override protected void onResume(){super.onResume();if(player!=null)player.setPlayWhenReady(playWhenReady&&!completed);}
 @Override protected void onStop(){
  handler.removeCallbacks(saver);if(player!=null){savePosition();position=player.getCurrentPosition();playerView.setPlayer(null);player.release();player=null;ready=false;}
  WindowManager.LayoutParams params=getWindow().getAttributes();params.preferredDisplayModeId=originalDisplayMode;getWindow().setAttributes(params);super.onStop();
 }
 @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle state){state.putLong("position",player==null?position:player.getCurrentPosition());state.putBoolean("playing",player==null?playWhenReady:player.getPlayWhenReady());state.putBoolean("completed",completed);super.onSaveInstanceState(state);}
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)showOptions();return true;}
  if (playerView != null && event.getAction() == KeyEvent.ACTION_DOWN) {
   int key = event.getKeyCode();
   View focus = getCurrentFocus();
   PlaybackChoiceRow row = rowForFocus(focus);
   View playPause = playerView.findViewById(androidx.media3.ui.R.id.exo_play_pause);
   View progressBar = playerView.findViewById(androidx.media3.ui.R.id.exo_progress);
   if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
    if (!playerView.isControllerFullyVisible()) {
     playerView.showController();
     progressBar.requestFocus();
     return true;
    }
    if (focus == playPause || focus == playerView.findViewById(R.id.playerMore)) {
     return audioRow.focusActive();
    }
    if (row == audioRow) return captionRow.focusActive();
    if (row == captionRow) return speedRow.focusActive();
    if (row == speedRow) return true;
   }
   if (key == KeyEvent.KEYCODE_DPAD_UP) {
    if (row == speedRow) return captionRow.focusActive();
    if (row == captionRow) return audioRow.focusActive();
    if (row == audioRow) return playPause.requestFocus();
   }
  }
  return super.dispatchKeyEvent(event);
 }
}
