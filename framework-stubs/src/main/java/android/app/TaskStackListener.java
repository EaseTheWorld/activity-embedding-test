package android.app;

import android.content.ComponentName;

/**
 * Compile-time stub for `@hide` android.app.TaskStackListener.
 * At runtime, this class is provided by Android framework (`framework.jar`).
 */
public abstract class TaskStackListener extends ITaskStackListener.Stub {
    public TaskStackListener() {}

    @Override
    public void onTaskStackChanged() {}

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int rootTaskId) {}

    @Override
    public void onActivityUnpinned() {}

    @Override
    public void onActivityRestartAttempt(
            ActivityManager.RunningTaskInfo task,
            boolean homeTaskVisible,
            boolean clearedTask,
            boolean wasVisible) {}

    @Override
    public void onActivityForcedResizable(String packageName, int taskId, int reason) {}

    @Override
    public void onActivityDismissingDockedTask() {}

    @Override
    public void onActivityLaunchOnSecondaryDisplayFailed(
            ActivityManager.RunningTaskInfo taskInfo, int requestedDisplayId) {}

    public void onActivityLaunchOnSecondaryDisplayFailed() {}

    @Override
    public void onActivityLaunchOnSecondaryDisplayRerouted(
            ActivityManager.RunningTaskInfo taskInfo, int requestedDisplayId) {}

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) {}

    @Override
    public void onTaskRemoved(int taskId) {}

    @Override
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {}

    public void onTaskMovedToFront(int taskId) {}

    @Override
    public void onTaskRemovalStarted(ActivityManager.RunningTaskInfo taskInfo) {}

    public void onTaskRemovalStarted(int taskId) {}

    @Override
    public void onTaskDescriptionChanged(ActivityManager.RunningTaskInfo taskInfo) {}

    public void onTaskDescriptionChanged(int taskId, ActivityManager.TaskDescription td) {}

    @Override
    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) {}

    @Override
    public void onTaskProfileLocked(ActivityManager.RunningTaskInfo taskInfo, int userId) {}

    public void onTaskProfileLocked(ActivityManager.RunningTaskInfo taskInfo) {}

    @Override
    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {}

    @Override
    public void onTaskDisplayChanged(int taskId, int newDisplayId) {}

    @Override
    public void onRecentTaskListUpdated() {}

    @Override
    public void onRecentTaskListFrozenChanged(boolean frozen) {}

    @Override
    public void onRecentTaskRemovedForAddTask(int taskId) {}

    @Override
    public void onTaskFocusChanged(int taskId, boolean focused) {}

    @Override
    public void onActivityRotation(int displayId) {}

    @Override
    public void onTaskMovedToBack(ActivityManager.RunningTaskInfo taskInfo) {}

    @Override
    public void onLockTaskModeChanged(int mode) {}
}
