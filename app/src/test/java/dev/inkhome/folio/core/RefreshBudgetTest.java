package dev.inkhome.folio.core;

import org.junit.Test;
import static dev.inkhome.folio.core.RefreshBudget.Change.*;
import static org.junit.Assert.*;

public class RefreshBudgetTest {
    private final RefreshBudget budget = new RefreshBudget();

    @Test public void defaultCadenceAndReset() {
        for (int i = 0; i < 7; i++) assertFalse(budget.charge(PAGE, false, 8));
        assertTrue(budget.charge(PAGE, false, 8));
        budget.reset();
        assertFalse(budget.charge(PAGE, false, 8));
    }

    @Test public void imageOverlayAndNightHaveDifferentCosts() {
        assertFalse(budget.charge(IMAGE, true, 8));
        assertFalse(budget.charge(OVERLAY, true, 8));
        assertFalse(budget.charge(OVERLAY, false, 8));
        assertTrue(budget.charge(IMAGE, false, 8));
    }

    @Test public void zeroDisablesCadenceAndChangingThresholdTakesEffect() {
        assertFalse(budget.charge(IMAGE, true, 8));
        assertFalse(budget.charge(PAGE, false, 0));
        assertFalse(budget.charge(PAGE, false, 4));
        assertTrue(budget.charge(PAGE, false, 1));
    }
}
