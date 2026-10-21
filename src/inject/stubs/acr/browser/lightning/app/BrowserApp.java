package acr.browser.lightning.app;

/** Compilation-only signature matching the user's locally inspected 1DM+ 18.2.
 * This class MUST NOT be put in the injected DEX. The original app supplies it.
 */
public class BrowserApp extends android.app.Application {
    @Override public void onCreate() { super.onCreate(); }
}
