package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Compile-time stub for `@hide` android.app.IUidObserver.
 * At runtime, this class is provided by Android framework (`framework.jar`).
 */
public interface IUidObserver extends IInterface {
    void onUidGone(int uid, boolean disabled);
    void onUidActive(int uid);
    void onUidIdle(int uid, boolean disabled);
    void onUidStateChanged(int uid, int procState, long procStateSeq, int capability);
    void onUidProcAdjChanged(int uid, int adj);
    void onUidCachedChanged(int uid, boolean cached);

    abstract class Stub extends Binder implements IUidObserver {
        public Stub() {}

        public static IUidObserver asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}
