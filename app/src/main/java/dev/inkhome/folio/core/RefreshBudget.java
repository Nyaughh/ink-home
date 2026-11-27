package dev.inkhome.folio.core;

/** Counts partial updates since the most recent clean. A zero threshold disables automatic cleans. */
public final class RefreshBudget {
    public enum Change { PAGE, IMAGE, OVERLAY }

    private long spent;

    public boolean charge(Change change, boolean night, int threshold) {
        if (threshold <= 0) {
            reset();
            return false;
        }
        spent += (change == Change.IMAGE ? 2 : 1) * (night ? 2 : 1);
        return spent >= threshold;
    }

    public void reset() {
        spent = 0;
    }
}
