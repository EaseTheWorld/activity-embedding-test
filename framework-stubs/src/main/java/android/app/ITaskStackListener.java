package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Compile-time stub for `@hide` android.app.ITaskStackListener.
 * At runtime, this class is provided by Android framework (`framework.jar`).
 */
public interface ITaskStackListener extends IInterface {
    int FORCED_RESIZEABLE_REASON_SPLIT_SCREEN = 1;
    int FORCED_RESIZEABLE_REASON_SECONDARY_DISPLAY = 2;

    void onTaskStackChanged();
    void onActivityPinned(String packageName, int userId, int taskId, int rootTaskId);
    void onActivityUnpinned();
    void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task, boolean homeTaskVisible, boolean clearedTask, boolean wasVisible);
    void onActivityForcedResizable(String packageName, int taskId, int reason);
    void onActivityDismissingDockedTask();
    void onActivityLaunchOnSecondaryDisplayFailed(ActivityManager.RunningTaskInfo taskInfo, int requestedDisplayId);
    void onActivityLaunchOnSecondaryDisplayRerouted(ActivityManager.RunningTaskInfo taskInfo, int requestedDisplayId);
    void onTaskCreated(int taskId, ComponentName componentName);
    void onTaskRemoved(int taskId);
    void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo);
    void onTaskDescriptionChanged(ActivityManager.RunningTaskInfo taskInfo);
    void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation);
    void onTaskRemovalStarted(ActivityManager.RunningTaskInfo taskInfo);
    void onTaskProfileLocked(ActivityManager.RunningTaskInfo taskInfo, int userId);
    void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo);
    void onTaskDisplayChanged(int taskId, int newDisplayId);
    void onRecentTaskListUpdated();
    void onRecentTaskListFrozenChanged(boolean frozen);
    void onRecentTaskRemovedForAddTask(int taskId);
    void onTaskFocusChanged(int taskId, boolean focused);
    void onActivityRotation(int displayId);
    void onTaskMovedToBack(ActivityManager.RunningTaskInfo taskInfo);
    void onLockTaskModeChanged(int mode);

    abstract class Stub extends Binder implements ITaskStackListener {
        public Stub() {}

        public static ITaskStackListener asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}
