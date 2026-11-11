package android.app;

/**
 * Compile-time stub for `@hide` android.app.UidObserver.
 * At runtime, this class is provided by Android framework (`framework.jar`).
 */
public class UidObserver extends IUidObserver.Stub {
    public UidObserver() {}

    @Override
    public void onUidActive(int uid) {}

    @Override
    public void onUidCachedChanged(int uid, boolean cached) {}

    @Override
    public void onUidGone(int uid, boolean disabled) {}

    @Override
    public void onUidIdle(int uid, boolean disabled) {}

    @Override
    public void onUidProcAdjChanged(int uid, int adj) {}

    @Override
    public void onUidStateChanged(int uid, int procState, long procStateSeq, int capability) {}
}
