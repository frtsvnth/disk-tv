package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Main screen: lists discovered DLNA MediaServers ("диски в сети") and recent folders.
 * CleanTV47-inspired: black background, glass tiles, D-pad friendly.
 */
public class MainActivity extends AppCompatActivity implements Discovery.Callback {

    private AppUpdates updates;
    private Discovery discovery;
    private ServerAdapter serverAdapter;
    private RecentAdapter recentAdapter;

    private RecyclerView serversList;
    private RecyclerView recentList;
    private TextView emptyServers;
    private TextView emptyRecent;
    private Button btnRefresh;
    private TextView networkStatus;
    private boolean firstDiscovery = true;

    private List<Server> servers = new ArrayList<>();
    private List<RecentFolder> recents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        updates = new AppUpdates(this, savedInstanceState);


        serversList = findViewById(R.id.serversList);
        recentList = findViewById(R.id.recentList);
        emptyServers = findViewById(R.id.emptyServers);
        emptyRecent = findViewById(R.id.emptyRecent);
        btnRefresh = findViewById(R.id.btnRefresh);
        networkStatus = findViewById(R.id.networkStatus);

        serverAdapter = new ServerAdapter(this, servers, this::onServerClicked);
        serversList.setAdapter(serverAdapter);
        serversList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        recentAdapter = new RecentAdapter(this, recents, this::onRecentClicked);
        recentList.setAdapter(recentAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recentList.setLayoutManager(llm);

        btnRefresh.setOnClickListener(v -> startDiscovery());
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        discovery = new Discovery(this, this);
        startDiscovery();
        loadRecents();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discovery != null) discovery.stop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        updates.saveState(state);
        super.onSaveInstanceState(state);
    }

    private void startDiscovery() {
        btnRefresh.setEnabled(false);
        btnRefresh.setText(getString(R.string.search_short));
        networkStatus.setText(R.string.discovering);
        emptyServers.setText(R.string.discovering);
        emptyServers.setVisibility(servers.isEmpty() ? View.VISIBLE : View.GONE);
        discovery.start();
    }

    @Override
    public void onServers(List<Server> found) {
        btnRefresh.setEnabled(true);
        btnRefresh.setText(getString(R.string.refresh));
        servers.clear();
        servers.addAll(found);
        loadRecents();
        serverAdapter.notifyDataSetChanged();
        if (firstDiscovery && !servers.isEmpty() && !recentList.hasFocus() && hasWindowFocus()) serversList.post(() -> serversList.requestFocus());
        firstDiscovery = false;
        networkStatus.setText(servers.isEmpty() ? getString(R.string.network_idle)
                : getResources().getQuantityString(R.plurals.server_count, servers.size(), servers.size()));
        emptyServers.setText(R.string.no_servers);
        emptyServers.setVisibility(servers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onError(String message) {
        btnRefresh.setEnabled(true);
        btnRefresh.setText(getString(R.string.refresh));
        networkStatus.setText(R.string.error_network);
        emptyServers.setText(R.string.no_servers);
        emptyServers.setVisibility(servers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onServerClicked(Server server) {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("server", server);
        intent.putExtra("objectId", "0");
        intent.putExtra("path", server.friendlyName);
        startActivity(intent);
    }

    private void onRecentClicked(RecentFolder recent) {
        Server resolved = recent.server;
        for (Server s : servers) if (s.udn.equals(recent.server.udn)) { resolved = s; break; }
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("server", resolved);
        intent.putExtra("objectId", recent.objectId);
        intent.putExtra("path", recent.title);
        startActivity(intent);
    }

    private void loadRecents() {
        recents.clear();
        for (RecentFolder r : new RecentStore(this).load()) {
            if ("0".equals(r.objectId)) continue;
            Server resolved = r.server;
            for (Server s : servers) if (s.udn.equals(r.server.udn)) { resolved = s; break; }
            recents.add(new RecentFolder(resolved, r.objectId, r.title));
        }
        recentAdapter.notifyDataSetChanged();
        emptyRecent.setVisibility(recents.isEmpty() ? View.VISIBLE : View.GONE);
        recentList.setVisibility(recents.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public static void addRecent(Context context, Server server, DmsItem folder) {
        new RecentStore(context).add(server, folder);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updates.check(false);
        loadRecents();
    }

    // ---- adapters ----

    private static class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {
        private final Context ctx;
        private final List<Server> items;
        private final ServerListener listener;

        ServerAdapter(Context ctx, List<Server> items, ServerListener listener) {
            this.ctx = ctx;
            this.items = items;
            this.listener = listener;
            setHasStableIds(true);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_server, parent, false);
            Ui.focus(v, true);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int pos) {
            Server s = items.get(pos);
            h.name.setText(s.friendlyName);
            h.addr.setText(s.address + ":" + s.port);
            h.itemView.setOnClickListener(v -> listener.onServer(s));
        }

        @Override
        public long getItemId(int position) { return Ui.identity(items.get(position).udn); }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView addr;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.serverName);
                addr = v.findViewById(R.id.serverAddress);
            }
        }
    }

    private static class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {
        private final Context ctx;
        private final List<RecentFolder> items;
        private final RecentListener listener;

        RecentAdapter(Context ctx, List<RecentFolder> items, RecentListener listener) {
            this.ctx = ctx;
            this.items = items;
            this.listener = listener;
            setHasStableIds(true);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_recent, parent, false);
            Ui.focus(v, true);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int pos) {
            RecentFolder r = items.get(pos);
            h.title.setText(Ui.leaf(r.title));
            String parent = Ui.parent(r.title);
            h.subtitle.setText(parent.isEmpty() ? r.server.friendlyName : parent);
            h.itemView.setOnClickListener(v -> listener.onRecent(r));
        }

        @Override
        public long getItemId(int position) {
            RecentFolder r = items.get(position);
            return Ui.identity(r.server.udn.length() + ":" + r.server.udn + r.objectId);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.itemTitle);
                subtitle = v.findViewById(R.id.itemSubtitle);
            }
        }
    }

    private interface ServerListener { void onServer(Server s); }
    private interface RecentListener { void onRecent(RecentFolder r); }

    /** A recently visited folder. */
    public static class RecentFolder {
        public final Server server;
        public final String objectId;
        public final String title;
        public RecentFolder(Server server, String objectId, String title) {
            this.server = server; this.objectId = objectId; this.title = title;
        }
    }
}
