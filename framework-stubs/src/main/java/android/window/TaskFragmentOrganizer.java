package android.window;

import java.util.concurrent.Executor;

/**
 * Compile-time stub for `@hide` android.window.TaskFragmentOrganizer.
 */
public class TaskFragmentOrganizer extends WindowOrganizer {

    public TaskFragmentOrganizer(Executor executor) {}

    public void registerOrganizer() {
        throw new RuntimeException("Stub!");
    }

    public void unregisterOrganizer() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void applyTransaction(WindowContainerTransaction t) {
        throw new RuntimeException("Stub!");
    }
}
