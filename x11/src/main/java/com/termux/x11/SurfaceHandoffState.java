package com.termux.x11;

/**
 * Tracks deferred X11 surface handoff across server stalls without watchdog polling.
 */
public final class SurfaceHandoffState {
    private volatile boolean surfacePending;

    public boolean isSurfacePending() {
        return surfacePending;
    }

    public boolean markPendingIfStalled(boolean stalled) {
        if (stalled) {
            surfacePending = true;
            return true;
        }
        return false;
    }

    public boolean consumePendingOnHeartbeat() {
        if (!surfacePending)
            return false;
        surfacePending = false;
        return true;
    }

    public void clear() {
        surfacePending = false;
    }
}
