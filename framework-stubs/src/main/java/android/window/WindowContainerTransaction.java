package android.window;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Compile-time stub for `@hide` android.window.WindowContainerTransaction.
 * At runtime, this class is provided by Android framework (`framework.jar`).
 */
public class WindowContainerTransaction implements Parcelable {

    public WindowContainerTransaction() {}

    public WindowContainerTransaction requestFocusOnTaskFragment(IBinder fragmentToken) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {}

    public static final Creator<WindowContainerTransaction> CREATOR = new Creator<WindowContainerTransaction>() {
        @Override
        public WindowContainerTransaction createFromParcel(Parcel in) {
            return new WindowContainerTransaction();
        }

        @Override
        public WindowContainerTransaction[] newArray(int size) {
            return new WindowContainerTransaction[size];
        }
    };
}
