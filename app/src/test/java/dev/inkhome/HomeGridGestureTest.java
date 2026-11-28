package dev.inkhome;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HomeGridGestureTest {
    private final HomeLayout layout = new HomeLayout();
    private final HomeGrid grid = mock(HomeGrid.class);
    private final View handle = mock(View.class);
    private final HomeLayout.Item item = new HomeLayout.Item("clock", "default", 0, 0, 1, 1);

    public HomeGridGestureTest() {
        layout.cols = 5;
        layout.items.add(item);
        when(grid.getWidth()).thenReturn(500);
        // Intrinsic first track followed by 30-pixel tracks.
        when(grid.rowSpan(eq(0), anyInt())).thenAnswer(call -> {
            int row = call.getArgument(1);
            return row == 0 ? 0 : 100 + (row - 1) * 30;
        });
        when(handle.getParent()).thenReturn(mock(ViewParent.class));
    }

    private void touch(HomeGridGesture gesture, int action, int x, int y) {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getActionMasked()).thenReturn(action);
        when(event.getRawX()).thenReturn((float) x);
        when(event.getRawY()).thenReturn((float) y);
        assertTrue(gesture.onTouch(handle, event));
    }

    @Test public void dragAcrossVariableTracksCommitsOnlyOnDrop() {
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, false, 8);
        touch(gesture, MotionEvent.ACTION_DOWN, 40, 50);
        touch(gesture, MotionEvent.ACTION_MOVE, 449, 180);
        touch(gesture, MotionEvent.ACTION_MOVE, 450, 181);
        verify(grid, times(1)).showTarget(4, 2, 1, 1);
        assertEquals(0, item.x);
        touch(gesture, MotionEvent.ACTION_UP, 450, 181);
        assertEquals(4, item.x);
        assertEquals(2, item.y);
        verify(grid).dropped(item);
    }

    @Test public void cancelledResizeLeavesIntrinsicHeightAndBoundsIntact() {
        item.settings.put("heightMode", "content");
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, true, 8);
        touch(gesture, MotionEvent.ACTION_DOWN, 100, 100);
        touch(gesture, MotionEvent.ACTION_MOVE, 200, 161);
        verify(grid).showTarget(0, 0, 2, 3);
        touch(gesture, MotionEvent.ACTION_CANCEL, 200, 161);
        verify(grid).dragging(false);
        assertEquals(1, item.h);
        assertEquals("content", item.settings.get("heightMode"));
        touch(gesture, MotionEvent.ACTION_DOWN, 100, 100);
        touch(gesture, MotionEvent.ACTION_UP, 200, 161);
        assertEquals(3, item.h);
        assertNull(item.settings.get("heightMode"));
    }

    @Test public void tapRetainsAccessibleClickAction() {
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, false, 8);
        touch(gesture, MotionEvent.ACTION_DOWN, 10, 10);
        touch(gesture, MotionEvent.ACTION_UP, 13, 12);
        verify(handle).performClick();
        verify(grid, never()).dragging(true);
    }
}
