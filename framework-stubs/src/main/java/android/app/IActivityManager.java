package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Compile-time stub for `@hide` android.app.IActivityManager.
 * At runtime, this class is provided by Android framework (`framework.jar`).
 */
public interface IActivityManager extends IInterface {
    void registerUidObserver(IUidObserver observer, int which, int cutpoint, String callingPackage);
    void unregisterUidObserver(IUidObserver observer);

    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
