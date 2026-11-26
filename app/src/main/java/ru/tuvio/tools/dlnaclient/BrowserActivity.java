package ru.tuvio.tools.dlnaclient;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * File/folder browser for a DLNA MediaServer.
 * - Shows children of the current objectId.
 * - Click a folder -> descend into it (new BrowserActivity on the back stack).
 * - Click "up" -> go back to parent folder.
 * - Click a video file -> check resume store, then play.
 */
public class BrowserActivity extends AppCompatActivity implements UpnpClient.Callback {


    private static final int VIMU_PLAYBACK_REQUEST = 121;
    private DmsItem pendingVimuItem;
    private String pendingVimuPackage;
    private Server server;
    private String objectId;
    private String path;

    private RecyclerView list;
    private TextView tvPath;
    private ImageButton btnUp;
    private BrowseAdapter adapter;
    private List<DmsItem> items = new ArrayList<>();
    private final List<DmsItem> serverOrder = new ArrayList<>();

    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        if (savedInstanceState != null) {
            pendingVimuItem = (DmsItem) savedInstanceState.getSerializable("vimuItem");
            pendingVimuPackage = savedInstanceState.getString("vimuPackage");
        }

        server = (Server) getIntent().getSerializableExtra("server");
        objectId = getIntent().getStringExtra("objectId");
        path = getIntent().getStringExtra("path");

        if (server == null) {
            finish();
            return;
        }

        tvPath = findViewById(R.id.tvPath);
        btnUp = findViewById(R.id.btnUp);
        list = findViewById(R.id.browseList);

        String fullPath = path != null ? path : server.friendlyName;
        String parentPath = Ui.parent(fullPath);
        tvPath.setText(parentPath.isEmpty() ? getString(R.string.network_drives) : parentPath);
        ((TextView) findViewById(R.id.folderTitle)).setText(Ui.leaf(fullPath));

        btnUp.setOnClickListener(v -> goUp());

        adapter = new BrowseAdapter(this, server.udn, items, this::onItemClicked, this::onItemLongClicked);
        list.setAdapter(adapter);
        list.setLayoutManager(new LinearLayoutManager(this));

        if (objectId == null) objectId = "0";
        loadFolder(objectId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) { items.clear(); items.addAll(serverOrder); new SortPreferences(this).sort(items); adapter.notifyDataSetChanged(); }
    }

    private void loadFolder(String objectId) {
        if (loading) return;
        loading = true;
        TextView status = findViewById(R.id.emptyBrowse);
        status.setText(R.string.browsing);
        status.setVisibility(View.VISIBLE);
        UpnpClient.browse(server, objectId, 0, 200, this);
    }

    @Override
    public void onItems(List<DmsItem> result) {
        if (isFinishing() || isDestroyed()) return;
        loading = false;
        items.clear();
        serverOrder.clear(); serverOrder.addAll(result);
        MediaDetails.attachSidecars(serverOrder);
        items.addAll(serverOrder);
        new SortPreferences(this).sort(items);
        int folders = 0;
        for (DmsItem item : items) if (item.isFolder) folders++;
        int files = items.size() - folders;
        String counts = folders == 0 ? "" : getResources().getQuantityString(R.plurals.folder_count, folders, folders);
        if (files > 0) counts += (counts.isEmpty() ? "" : " · ")
                + getResources().getQuantityString(R.plurals.file_count, files, files);
        ((TextView) findViewById(R.id.folderCount)).setText(counts);
        adapter.notifyDataSetChanged();
        if (!items.isEmpty()) list.post(() -> list.requestFocus());

        TextView empty = findViewById(R.id.emptyBrowse);
        if (items.isEmpty()) {
            empty.setText(R.string.empty_folder);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }

        // Add current folder to recents.
        MainActivity.addRecent(this, server, new DmsItem(objectId, null, path, null, null, 0, true, null, null));
    }

    @Override
    public void onError(String message) {
        if (isFinishing() || isDestroyed()) return;
        loading = false;
        TextView status = findViewById(R.id.emptyBrowse);
        status.setText(R.string.browse_retry);
        status.setVisibility(View.VISIBLE);
        status.setOnClickListener(v -> loadFolder(objectId));
        status.setFocusable(true);
    }

    private void onItemClicked(DmsItem item) {
        if (item.isFolder) {
            // Descend into folder.
            String newPath = (path == null || path.isEmpty()) ? item.title : path + " / " + item.title;
            Intent intent = new Intent(this, BrowserActivity.class);
            intent.putExtra("server", server);
            intent.putExtra("objectId", item.objectId);
            intent.putExtra("path", newPath);
            startActivity(intent);
        } else {
            if (!canPlay(item)) return;
            String preferred = new PlayerPreferences(this).get();
            if (PlayerPreferences.INTERNAL.equals(preferred)) playInternal(item);
            else {
                PlayerResolver.Option option = PlayerResolver.find(this, item, preferred);
                if (option == null || !playExternal(item, option)) {
                    Toast.makeText(this, R.string.player_unavailable_hint, Toast.LENGTH_LONG).show();
                    choosePlayer(item);
                }
            }
        }
    }

    private boolean canPlay(DmsItem item) {
        String mime = item.mimeType == null ? "" : item.mimeType.toLowerCase(java.util.Locale.ROOT);
        String title = item.title.toLowerCase(java.util.Locale.ROOT);
        boolean video = mime.startsWith("video/") || mime.startsWith("object.item.videoitem")
                || title.endsWith(".mp4") || title.endsWith(".mkv") || title.endsWith(".avi")
                || title.endsWith(".mov") || title.endsWith(".ts") || title.endsWith(".m2ts");
        if (!video || item.url == null || item.url.isEmpty()) {
            Toast.makeText(this, R.string.unsupported_file, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void onItemLongClicked(DmsItem item) {
        if (!item.isFolder && canPlay(item)) choosePlayer(item);
    }

    private void choosePlayer(DmsItem item) {
        ResumeStore history = new ResumeStore(this);
        boolean watched = history.isWatched(server.udn, item.objectId);
        PlayerPicker.show(this, item, (option, remember) -> {
            if (option.internal()) {
                if (remember) new PlayerPreferences(this).set(option.id);
                playInternal(item);
            } else if (playExternal(item, option)) {
                if (remember) new PlayerPreferences(this).set(option.id);
            } else {
                Toast.makeText(this, R.string.player_unavailable_hint, Toast.LENGTH_LONG).show();
                choosePlayer(item);
            }
        }, watched ? R.string.mark_unwatched : R.string.mark_watched, () -> {
            history.setWatched(server.udn, item.objectId, !watched);
            int position = items.indexOf(item);
            if (position >= 0) adapter.notifyItemChanged(position);
        });
    }

    private void playInternal(DmsItem item) {
        ResumeStore.ResumeEntry entry = new ResumeStore(this).load(server.udn, item.objectId);
        if (entry != null && entry.hasResume()) showResumeDialog(item, entry, ms -> play(item, ms));
        else play(item, 0);
    }

    private boolean playExternal(DmsItem item, PlayerResolver.Option option) {
        String target = option.component.getPackageName();
        if (!target.equals("net.gtvbox.videoplayer") && !target.equals("net.gtvbox.vimuhd")) {
            return PlayerResolver.launch(this, item, option);
        }
        ResumeStore.ResumeEntry entry = new ResumeStore(this).load(server.udn, item.objectId);
        if (entry != null && entry.hasResume()) {
            showResumeDialog(item, entry, ms -> launchVimu(item, option, ms));
            return true;
        }
        return launchVimu(item, option, 0);
    }

    private boolean launchVimu(DmsItem item, PlayerResolver.Option option, long startMs) {
        Intent intent = PlayerResolver.videoIntent(item).setComponent(option.component)
                .putExtra("forcename", item.title)
                .putExtra("startfrom", (int) Math.min(Integer.MAX_VALUE, startMs));
        pendingVimuItem = item;
        pendingVimuPackage = option.component.getPackageName();
        try {
            startActivityForResult(intent, VIMU_PLAYBACK_REQUEST);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            pendingVimuItem = null;
            pendingVimuPackage = null;
            Toast.makeText(this, R.string.player_unavailable_hint, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putSerializable("vimuItem", pendingVimuItem);
        state.putString("vimuPackage", pendingVimuPackage);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != VIMU_PLAYBACK_REQUEST) return;
        DmsItem item = pendingVimuItem;
        String source = pendingVimuPackage;
        pendingVimuItem = null;
        pendingVimuPackage = null;
        if (item == null || server == null || source == null || data == null) return;
        String action = data.getAction();
        if (!"net.gtvbox.videoplayer.result".equals(action) && !(source + ".result").equals(action)) return;

        ResumeStore history = new ResumeStore(this);
        if (result == 1) {
            history.setWatched(server.udn, item.objectId, true);
        } else if (result == 0) {
            long position = data.getIntExtra("position", -1);
            long duration = data.getIntExtra("duration", -1);
            if (position > 1000 && (duration <= 0 || position <= duration)) {
                history.save(server.udn, item.objectId, item.title, item.url, position, duration);
            }
        }
    }

    private void goUp() { finish(); }

    private void showResumeDialog(DmsItem item, ResumeStore.ResumeEntry entry, java.util.function.LongConsumer start) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_resume);
        d.setCanceledOnTouchOutside(false);

        TextView body = d.findViewById(R.id.dialogBody);
        body.setText(item.title);
        ((TextView) d.findViewById(R.id.dialogPosition)).setText(Ui.time(entry.positionMs));
        ProgressBar progress = d.findViewById(R.id.dialogProgress);
        progress.setVisibility(entry.durationMs > 0 ? View.VISIBLE : View.GONE);
        if (entry.durationMs > 0) progress.setProgress((int) Math.min(1000, entry.positionMs * 1000 / entry.durationMs));

        Button btnResume = d.findViewById(R.id.btnResume);
        Button btnStart = d.findViewById(R.id.btnStartOver);

        btnResume.setOnClickListener(v -> {
            d.dismiss();
            start.accept(entry.positionMs);
        });
        btnStart.setOnClickListener(v -> {
            d.dismiss();
            start.accept(0);
        });

        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(Ui.dp(this, 520), getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 96));
            d.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setDimAmount(0.72f);
        }
        btnResume.requestFocus();
    }

    private void play(DmsItem item, long startMs) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("server", server);
        intent.putExtra("item", item);
        intent.putExtra("startMs", startMs);
        startActivity(intent);
    }

    // ---- adapter ----

    private static class BrowseAdapter extends RecyclerView.Adapter<BrowseAdapter.ViewHolder> {
        private final Context ctx;
        private final List<DmsItem> items;
        private final ItemListener listener;
        private final ItemListener longListener;
        private final String serverId;
        private final ResumeStore resume;

        BrowseAdapter(Context ctx, String serverId, List<DmsItem> items, ItemListener listener, ItemListener longListener) {
            this.ctx = ctx;
            this.serverId = serverId;
            this.resume = new ResumeStore(ctx);
            this.items = items;
            this.listener = listener;
            this.longListener = longListener;
            setHasStableIds(true);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_browse, parent, false);
            Ui.focus(v, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int pos) {
            DmsItem it = items.get(pos);
            h.title.setText(it.title);
            h.subtitle.setText(Ui.fileInfo(ctx, it));
            ResumeStore.ResumeEntry progress = it.isFolder ? null : resume.load(serverId, it.objectId);
            boolean resumable = progress != null && progress.hasResume();
            boolean watched = !it.isFolder && resume.isWatched(serverId, it.objectId);
            h.status.setVisibility(resumable || watched ? View.VISIBLE : View.GONE);
            if (resumable) h.status.setText(Ui.time(progress.positionMs));
            else if (watched) h.status.setText(R.string.watched);
            boolean showProgress = resumable && progress.durationMs > 0;
            h.progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            if (showProgress) h.progress.setProgress((int) Math.min(1000, progress.positionMs * 1000 / progress.durationMs));
            h.icon.setImageResource(it.isFolder ? R.drawable.ic_folder : R.drawable.ic_file);
            h.arrow.setImageResource(it.isFolder ? R.drawable.ic_chevron : R.drawable.ic_play);
            h.itemView.setOnClickListener(v -> listener.onItem(it));
            h.itemView.setOnLongClickListener(it.isFolder ? null : v -> {
                longListener.onItem(it);
                return true;
            });
            h.itemView.setLongClickable(!it.isFolder);
        }

        @Override
        public int getItemCount() { return items.size(); }
        @Override public long getItemId(int position) { return Ui.identity(serverId + ":" + items.get(position).objectId); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView subtitle;
            final ImageView arrow;
            final TextView status;
            final ProgressBar progress;
            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.itemIcon);
                title = v.findViewById(R.id.itemTitle);
                subtitle = v.findViewById(R.id.itemSubtitle);
                arrow = v.findViewById(R.id.itemArrow);
                status = v.findViewById(R.id.itemStatus);
                progress = v.findViewById(R.id.itemProgress);
            }
        }
    }

    private interface ItemListener { void onItem(DmsItem item); }
}
