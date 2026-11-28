package dev.inkhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Registry shared by the renderer, editor, and persisted layout validator. */
final class HomeComponents {
    private HomeComponents() {}

    private static final List<HomeComponent> ALL = Collections.unmodifiableList(Arrays.asList(
            CurrentBookTile.COMPONENT, NextBooksTile.COMPONENT, FavoriteAppsTile.COMPONENT,
            ClockTile.COMPONENT, CalendarTile.COMPONENT, ReadingGoalTile.COMPONENT,
            CalendarTile.READING_MONTH, BatteryTile.COMPONENT, new AndroidWidgetTile()));

    static List<HomeComponent> all() { return ALL; }
    static HomeComponent get(String id) {
        for (HomeComponent component : ALL) if (component.id().equals(id)) return component;
        return null;
    }
}
