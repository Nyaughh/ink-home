package dev.inkhome.lab;

import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.widget.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class DataGallery {
    private DataGallery() {}

    public static void add(LinearLayout content, Folio f) {
        section(content, f, "Actions and filters");
        TextView feedback = Type.text(f, "No action selected", TypeScale.META, f.palette.ink2);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(feedback);

        View chip = Chip.create(f, "Reading", v -> {
            v.setVisibility(View.GONE);
            feedback.setText("Reading filter removed");
        });
        content.addView(chip, -2, -2);
        content.addView(Button.create(f, "Show reading filter", Button.Style.SECONDARY, v -> {
            chip.setVisibility(View.VISIBLE);
            feedback.setText("Reading filter active");
        }), -2, -2);
        content.addView(IconButton.create(f, "search", "Search books",
                v -> feedback.setText("Search selected")), -2, -2);

        TextView unavailable = Button.create(f, "Open selection", Button.Style.SECONDARY, null);
        unavailable.setEnabled(false);
        if (!f.displayClass.isEink()) unavailable.setTextColor(f.palette.ink3);
        content.addView(unavailable, -2, -2);
        content.addView(Type.text(f, "Select a book to open it.", TypeScale.META, f.palette.ink2));

        section(content, f, "Reading preferences");
        RadioGroup radios = new RadioGroup(f.context);
        RadioButton recent = Radio.create(f, "Recently opened", true);
        RadioButton title = Radio.create(f, "Book title", false);
        recent.setId(View.generateViewId());
        title.setId(View.generateViewId());
        radios.addView(recent);
        radios.addView(title);
        radios.check(recent.getId());
        radios.setOnCheckedChangeListener((group, id) ->
                feedback.setText(id == recent.getId() ? "Sorted by recent activity" : "Sorted by title"));
        content.addView(radios);
        CheckBox finished = Check.create(f, "Include finished books", false);
        finished.setOnCheckedChangeListener((button, checked) ->
                feedback.setText(checked ? "Finished books included" : "Finished books hidden"));
        content.addView(finished);
        content.addView(Callout.create(f, "Show your books", "Choose a folder for your library.",
                "Choose folder", v -> feedback.setText("Folder action selected")));
        content.addView(EmptyState.create(f, "No matching books", "Try another filter.",
                "Clear filter", v -> {
                    chip.setVisibility(View.GONE);
                    feedback.setText("Filters cleared");
                }));

        section(content, f, "Control panel");
        Tile quiet = new Tile(f, "quiet", "Quiet", "Off", false, v -> {
            Tile tile = (Tile) v;
            boolean on = !tile.isSelected();
            tile.setState(on ? "On" : "Off", on);
        });
        content.addView(quiet);
        View dot = InkDot.create(f, v -> feedback.setText("Clean screen selected"), v -> {
            feedback.setText("Refresh options selected");
            return true;
        });
        content.addView(dot, -2, -2);

        section(content, f, "Reading activity");
        content.addView(Ledger.create(f,
                new String[]{"Streak", "This week", "This month", "All time", "Finished", "In progress"},
                new String[]{"4 days", "191 min", "584 min", "21 hours", "12", "3"}));
        content.addView(new BarChart(f, new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"},
                new int[]{14, 0, 32, 18, 48, 42, 37}, 30), -1, -2);
        content.addView(DataRow.create(f, "Field notes", .76f, "76%"));
        content.addView(DataRow.create(f, "Essays", .24f, "24%"));

        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        section(content, f, month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        int[] minutes = new int[month.lengthOfMonth()];
        for (int i = 0; i < today.getDayOfMonth(); i++) minutes[i] = (i * 13) % 52;
        TextView selection = Type.text(f, "Select a date", TypeScale.META, f.palette.ink2);
        selection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(selection);
        content.addView(MonthGrid.create(f, month, DayOfWeek.MONDAY, minutes, 30, today, date ->
                selection.setText(date + " · " + minutes[date.getDayOfMonth() - 1] + " minutes")));
    }

    private static void section(LinearLayout content, Folio f, String name) {
        TextView title = Type.text(f, name, TypeScale.TITLE_SM, f.palette.ink);
        title.setPadding(0, f.u(12), 0, f.u(4));
        title.setAccessibilityHeading(true);
        content.addView(title);
    }
}
