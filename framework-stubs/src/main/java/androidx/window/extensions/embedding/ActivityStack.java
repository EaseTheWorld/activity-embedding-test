package androidx.window.extensions.embedding;

import android.os.Bundle;
import android.os.IBinder;

public class ActivityStack {
    public static final class Token {
        public IBinder getRawToken() {
            throw new RuntimeException("Stub!");
        }

        public static Token createFromBinder(IBinder binder) {
            throw new RuntimeException("Stub!");
        }

        public Bundle toBundle() {
            throw new RuntimeException("Stub!");
        }
    }
}
