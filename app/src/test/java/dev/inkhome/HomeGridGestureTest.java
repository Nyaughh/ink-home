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
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, HomeGridGesture.Handle.MOVE, 8);
        touch(gesture, MotionEvent.ACTION_DOWN, 40, 50);
        touch(gesture, MotionEvent.ACTION_MOVE, 449, 180);
        touch(gesture, MotionEvent.ACTION_MOVE, 450, 181);
        verify(grid, times(1)).showTarget(4, 2, 1, 1);
        assertEquals(0, item.x);
        touch(gesture, MotionEvent.ACTION_UP, 450, 181);
        assertEquals(4, item.x);
        assertEquals(2, item.y);
        verify(grid).dropped();
        verify(handle, never()).performClick();
    }

    @Test public void cancelledResizeLeavesIntrinsicHeightAndBoundsIntact() {
        item.settings.put("heightMode", "content");
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, HomeGridGesture.Handle.BOTTOM_RIGHT, 8);
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

    @Test public void eachCornerExpandsAroundItsOppositeCornerAndReflowsANeighbor() {
        for (HomeGridGesture.Handle corner : HomeGridGesture.Handle.values()) {
            if (corner == HomeGridGesture.Handle.MOVE) continue;
            item.x = 2;
            item.y = 2;
            item.w = 1;
            item.h = 1;
            int x = corner.left ? 1 : 2;
            int y = corner.top ? 1 : 2;
            HomeLayout.Item neighbor = new HomeLayout.Item("clock", "default",
                    corner.left ? 1 : 3, corner.top ? 1 : 3, 1, 1);
            layout.items.add(neighbor);
            HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, corner, 8);
            touch(gesture, MotionEvent.ACTION_DOWN, 200, 200);
            touch(gesture, MotionEvent.ACTION_UP, corner.left ? 100 : 300, corner.top ? 170 : 230);
            assertEquals(x, item.x);
            assertEquals(y, item.y);
            assertEquals(2, item.w);
            assertEquals(2, item.h);
            assertTrue(layout.fits(item, x, y, 2, 2));
            layout.items.remove(neighbor);
        }
        verify(handle, never()).performClick();
        verify(grid, times(4)).dropped();
    }

    @Test public void draggingPastTopLeftBoundaryDoesNotClickOrShiftAnchor() {
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, HomeGridGesture.Handle.TOP_LEFT, 8);
        touch(gesture, MotionEvent.ACTION_DOWN, 10, 10);
        touch(gesture, MotionEvent.ACTION_UP, -100, -150);
        verify(grid).showTarget(0, 0, 1, 1);
        verify(grid).dropped();
        verify(handle, never()).performClick();
        assertNull(layout.resizeAt(item, -1, 0, 2, 1));
        assertNull(layout.resizeAt(item, 0, -1, 1, 2));
    }

    @Test public void tapRetainsAccessibleClickAction() {
        HomeGridGesture gesture = new HomeGridGesture(grid, layout, item, HomeGridGesture.Handle.MOVE, 8);
        touch(gesture, MotionEvent.ACTION_DOWN, 10, 10);
        touch(gesture, MotionEvent.ACTION_UP, 13, 12);
        verify(handle).performClick();
        verify(grid, never()).dragging(true);
    }
}
