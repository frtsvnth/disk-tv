package ru.tuvio.tools.dlnaclient;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.List;

final class PlayerPicker {
    interface Listener { void onSelected(PlayerResolver.Option player, boolean remember); }
    static void show(AppCompatActivity activity, DmsItem item, Listener listener) {
        show(activity, item, listener, 0, null);
    }
    static void show(AppCompatActivity activity, DmsItem item, Listener listener,
                     int historyLabel, Runnable historyAction) {
        List<PlayerResolver.Option> options = PlayerResolver.list(activity, item);
        String preferred = new PlayerPreferences(activity).get();
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_player_picker);
        ((TextView) dialog.findViewById(R.id.pickerTitle)).setText(item == null ? R.string.default_player : R.string.open_with_player);
        ((TextView) dialog.findViewById(R.id.pickerSubtitle)).setText(item == null ? activity.getString(R.string.compatible_players) : item.title);
        CheckBox remember = dialog.findViewById(R.id.rememberPlayer);
        remember.setVisibility(item == null ? View.GONE : View.VISIBLE);
        Button history = dialog.findViewById(R.id.toggleWatched);
        history.setVisibility(historyAction == null ? View.GONE : View.VISIBLE);
        if (historyAction != null) {
            history.setText(historyLabel);
            history.setOnClickListener(v -> {
                dialog.dismiss();
                historyAction.run();
            });
        }
        RecyclerView list = dialog.findViewById(R.id.playerOptions);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(new RecyclerView.Adapter<Holder>() {
            @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
                View view = LayoutInflater.from(activity).inflate(R.layout.item_player, parent, false);
                Ui.focus(view, false);
                return new Holder(view);
            }
            @Override public void onBindViewHolder(Holder holder, int position) {
                PlayerResolver.Option option = options.get(position);
                holder.icon.setImageDrawable(option.icon);
                holder.title.setText(option.label);
                holder.subtitle.setText(option.internal() ? R.string.internal_player_detail : R.string.external_player_detail);
                holder.selected.setVisibility(option.id.equals(preferred) ? View.VISIBLE : View.INVISIBLE);
                holder.itemView.setOnClickListener(v -> {
                    boolean save = item == null || remember.isChecked();
                    dialog.dismiss();
                    listener.onSelected(option, save);
                });
            }
            @Override public int getItemCount() { return options.size(); }
        });
        ViewGroup.LayoutParams params = list.getLayoutParams();
        int maxHeight = activity.getResources().getDisplayMetrics().heightPixels - Ui.dp(activity, item == null ? 190 : historyAction == null ? 238 : 288);
        params.height = Math.max(Ui.dp(activity, 64), Math.min(Ui.dp(activity, options.size() * 69 + 8), maxHeight));
        list.setLayoutParams(params);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(Math.min(Ui.dp(activity, 560), activity.getResources().getDisplayMetrics().widthPixels - Ui.dp(activity, 96)), ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setDimAmount(0.72f);
        }
        int selected = 0;
        for (int i = 0; i < options.size(); i++) if (options.get(i).id.equals(preferred)) selected = i;
        final int initial = selected;
        list.scrollToPosition(initial);
        list.post(() -> {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(initial);
            if (holder != null) holder.itemView.requestFocus();
            else list.requestFocus();
        });
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, subtitle, selected;
        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.playerIcon);
            title = view.findViewById(R.id.playerName);
            subtitle = view.findViewById(R.id.playerDetail);
            selected = view.findViewById(R.id.playerSelected);
        }
    }
}
