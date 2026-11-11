package android.app;

import android.os.IInterface;
import android.window.IWindowOrganizerController;

public interface IActivityTaskManager extends IInterface {
    IWindowOrganizerController getWindowOrganizerController();
    void setFocusedTask(int taskId);
    void setFocusedRootTask(int taskId);
    void registerTaskStackListener(ITaskStackListener listener);
    void unregisterTaskStackListener(ITaskStackListener listener);
}
