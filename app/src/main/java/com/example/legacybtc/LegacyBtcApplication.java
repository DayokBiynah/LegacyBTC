package com.example.legacybtc;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * I track visible Activities to distinguish internal navigation from leaving the app. When the
 * last screen stops outside configuration changes or credential authentication, I clear the
 * unlocked flag and request encrypted persistence. Returning to a secured device requires
 * authentication again. This lock hides UI access; it does not claim to erase all bitcoinj
 * keys from the Java heap.
 */
public class LegacyBtcApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private int visibleActivities;
    private boolean unlocked, authenticating;
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     */
    @Override public void onCreate() { super.onCreate(); registerActivityLifecycleCallbacks(this); }
    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean value) { unlocked = value; }
    public void setAuthenticating(boolean value) { authenticating = value; }
    /**
     * I increment the visible-activity counter supplied by Android lifecycle callbacks. This
     * shared count lets the Application distinguish moving between internal screens from
     * leaving the app entirely.
     * @param activity Activity supplied by the calling workflow as described above
     */
    @Override public void onActivityStarted(Activity activity) { visibleActivities++; }
    /**
     * I decrement the visible-activity counter without allowing it below zero. When the app
     * has no visible screen, outside configuration changes or authentication, I clear the
     * unlocked flag and submit persistence. A missing selected wallet is harmless; existing
     * wallet storage failures remain tracked by the service.
     * @param activity Activity supplied by the calling workflow as described above
     */
    @Override public void onActivityStopped(Activity activity) {
        visibleActivities = Math.max(0, visibleActivities - 1);
        if (visibleActivities == 0 && !activity.isChangingConfigurations() && !authenticating) {
            unlocked = false;
            BitcoinService.get(this).submit(() -> {
                try { BitcoinService.get(this).persist(); } catch (IllegalStateException ignored) { /* No wallet selected. */ }
                return true;
            });
        }
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
