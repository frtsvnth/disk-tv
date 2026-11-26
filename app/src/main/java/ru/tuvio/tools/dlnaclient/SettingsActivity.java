package ru.tuvio.tools.dlnaclient;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private AppUpdates updates;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        updates = new AppUpdates(this);
        findViewById(R.id.checkUpdates).setOnClickListener(v -> updates.check(true));
        findViewById(R.id.settingsBack).setOnClickListener(v -> finish());
        findViewById(R.id.chooseDefaultPlayer).setOnClickListener(v -> PlayerPicker.show(this, null, (option, remember) -> {
            new PlayerPreferences(this).set(option.id);
            updatePlayer();
        }));
        findViewById(R.id.sortField).setOnClickListener(v -> {
            SortPreferences sort = new SortPreferences(this);
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Сортировать по")
                .setSingleChoiceItems(new String[]{"Названию", "Размеру", "Порядку сервера", "Дате", "Длительности"}, sort.field(), (dialog, which) -> {
                    sort.field(which); dialog.dismiss(); updatePlayer();
                }).show();
        });
        findViewById(R.id.sortDirection).setOnClickListener(v -> {
            SortPreferences sort = new SortPreferences(this); sort.descending(!sort.descending()); updatePlayer();
        });
        findViewById(R.id.sortFolders).setOnClickListener(v -> {
            SortPreferences sort = new SortPreferences(this); sort.foldersFirst(!sort.foldersFirst()); updatePlayer();
        });
        configurePlayback();
        findViewById(R.id.chooseDefaultPlayer).requestFocus();
    }
    @Override protected void onResume() { super.onResume(); updatePlayer(); }
    private void updatePlayer() {
        SortPreferences sort = new SortPreferences(this);
        ((TextView)findViewById(R.id.sortField)).setText("Сортировка: " + new String[]{"название", "размер", "порядок сервера", "дата", "длительность"}[sort.field()]);
        ((TextView)findViewById(R.id.sortDirection)).setText(sort.descending()?"Направление: по убыванию":"Направление: по возрастанию");
        ((TextView)findViewById(R.id.sortFolders)).setText(sort.foldersFirst()?"Папки сначала: да":"Папки сначала: нет");
        ((TextView) findViewById(R.id.currentPlayer)).setText(PlayerResolver.preferredLabel(this));
        PlaybackPreferences p=new PlaybackPreferences(this);
        ((TextView)findViewById(R.id.audioLanguage)).setText("Язык аудио: "+languageLabel(p.string("audio_language","ru")));
        ((TextView)findViewById(R.id.subtitleLanguage)).setText("Субтитры по умолчанию: "+languageLabel(p.string("subtitle_language","forced")));
        ((TextView)findViewById(R.id.subtitleSize)).setText("Размер субтитров: "+p.subtitleSize());
        ((TextView)findViewById(R.id.subtitleStyle)).setText("Оформление субтитров: "+p.string("subtitle_style","original").replace("original","из файла").replace("white","белый").replace("yellow","жёлтый"));
        ((TextView)findViewById(R.id.seekStep)).setText("Шаг перемотки: "+(p.seekMs()/1000)+" с");
        ((TextView)findViewById(R.id.aspectMode)).setText("Масштаб: "+p.string("aspect","fit").replace("fit","вписать").replace("zoom","приблизить").replace("fill","растянуть"));
        ((TextView)findViewById(R.id.networkBuffer)).setText("Буфер сети: "+p.bufferMb()+" МБ");
        ((TextView)findViewById(R.id.frameMatching)).setText("Подстройка частоты кадров: "+(p.bool("frame_match",false)?"вкл":"выкл"));
    }
    private String languageLabel(String value){
        if (value.equals("forced")) return "Только форсированные";
        if(value.equals("off"))return "выключены";
        if(value.equals("system"))return "как в Android";
        return java.util.Locale.forLanguageTag(value).getDisplayLanguage();
    }
    private void configurePlayback(){
        stringChoice(R.id.audioLanguage,"Язык аудиодорожки",new String[]{"Как в Android","Русский","Английский","Украинский","Японский","Немецкий","Французский"},new String[]{"system","ru","en","uk","ja","de","fr"},"audio_language","ru");
        stringChoice(R.id.subtitleLanguage,"Субтитры по умолчанию",new String[]{"Только форсированные","Выключены","Как в Android","Русский","Английский","Украинский","Японский","Немецкий","Французский"},new String[]{"forced","off","system","ru","en","uk","ja","de","fr"},"subtitle_language","forced");
        stringChoice(R.id.subtitleStyle,"Оформление субтитров",new String[]{"Стили из файла","Белый с обводкой","Жёлтый с обводкой"},new String[]{"original","white","yellow"},"subtitle_style","original");
        stringChoice(R.id.aspectMode,"Масштаб изображения",new String[]{"Вписать целиком","Приблизить с обрезкой","Растянуть"},new String[]{"fit","zoom","fill"},"aspect","fit");
        intChoice(R.id.subtitleSize,"Размер субтитров",new String[]{"Мелкие","Обычные","Крупные","Очень крупные"},new int[]{18,22,28,34},"subtitle_size",22);
        intChoice(R.id.seekStep,"Шаг точной перемотки",new String[]{"1 секунда","2 секунды","5 секунд","10 секунд"},new int[]{1,2,5,10},"seek",1);
        intChoice(R.id.networkBuffer,"Буфер сети",new String[]{"16 МБ — меньше памяти","32 МБ — обычный","64 МБ — высокий битрейт"},new int[]{16,32,64},"buffer",32);
        findViewById(R.id.frameMatching).setOnClickListener(v->{PlaybackPreferences p=new PlaybackPreferences(this);p.set("frame_match",!p.bool("frame_match",false));updatePlayer();});
    }
    private void stringChoice(int view,String title,String[] labels,String[] values,String key,String fallback){
        findViewById(view).setOnClickListener(v->{
            PlaybackPreferences p=new PlaybackPreferences(this);int selected=java.util.Arrays.asList(values).indexOf(p.string(key,fallback));
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected,(d,which)->{p.set(key,values[which]);d.dismiss();updatePlayer();}).show();
        });
    }
    private void intChoice(int view,String title,String[] labels,int[] values,String key,int fallback){
        findViewById(view).setOnClickListener(v->{
            PlaybackPreferences p=new PlaybackPreferences(this);int selected=0;for(int i=0;i<values.length;i++)if(values[i]==p.integer(key,fallback))selected=i;
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected,(d,which)->{p.set(key,values[which]);if(key.equals("subtitle_size"))p.set("subtitle_size_custom",true);d.dismiss();updatePlayer();}).show();
        });
    }
}
