package org.local.tabgroups;

import acr.browser.lightning.app.BrowserApp;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** A launcher hook: extends the existing, unmodified 1DM+ Application class.
 * Do NOT bundle the compilation-only BrowserApp stub.
 */
public final class InjectedBrowserApp extends BrowserApp {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (TabGroupsOverlay.isBrowser(activity)) TabGroupsOverlay.install(activity);
            }
            @Override public void onActivityDestroyed(Activity activity) {
                TabGroupsOverlay.detach(activity);
            }
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
        });
    }
}
