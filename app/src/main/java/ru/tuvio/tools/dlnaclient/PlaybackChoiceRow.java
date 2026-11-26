package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** A row of remote-focusable choices. Focus and the active value are independent. */
public final class PlaybackChoiceRow extends LinearLayout {
    private final TextView title;
    private final LinearLayout choices;
    private List<String> labels = new ArrayList<>();
    private int active;
    private IntConsumer onChoose;

    public PlaybackChoiceRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER_VERTICAL);
        title = new TextView(context);
        title.setTextColor(0xffd1d9e5);
        title.setTextSize(14);
        addView(title, new LayoutParams(Ui.dp(context, 106), LayoutParams.WRAP_CONTENT));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFocusable(false);
        scroll.setHorizontalScrollBarEnabled(false);
        choices = new LinearLayout(context);
        choices.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(choices);
        addView(scroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    void bind(String heading, List<String> values, int selected, IntConsumer action) {
        title.setText(heading);
        active = selected;
        onChoose = action;
        if (!labels.equals(values)) {
            int previousFocus = choices.indexOfChild(choices.getFocusedChild());
            labels = new ArrayList<>(values);
            choices.removeAllViews();
            for (int i = 0; i < values.size(); i++) {
                TextView chip = new TextView(getContext());
                chip.setText(values.get(i));
                chip.setTextSize(13);
                chip.setSingleLine();
                chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setMaxWidth(Ui.dp(getContext(), 330));
                int padding = Ui.dp(getContext(), 12);
                chip.setPadding(padding, 0, padding, 0);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setTextColor(getContext().getColorStateList(R.color.button_text));
                chip.setBackgroundResource(R.drawable.playback_choice);
                chip.setFocusable(true);
                int index = i;
                chip.setOnClickListener(v -> onChoose.accept(index));
                LayoutParams layout = new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(getContext(), 35));
                layout.setMarginEnd(Ui.dp(getContext(), 8));
                choices.addView(chip, layout);
            }
            if (previousFocus >= 0 && choices.getChildCount() > 0) {
                choices.getChildAt(Math.min(previousFocus, choices.getChildCount() - 1)).requestFocus();
            }
        }
        for (int i = 0; i < choices.getChildCount(); i++) {
            View chip = choices.getChildAt(i);
            chip.setActivated(i == active);
            chip.setContentDescription(values.get(i) + (i == active ? ", выбрано" : ""));
        }
    }

    boolean focusActive() {
        View target = choices.getChildAt(Math.max(0, active));
        return target != null && target.requestFocus();
    }
}
