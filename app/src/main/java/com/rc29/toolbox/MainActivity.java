package com.rc29.toolbox;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final int[] NAV = {R.id.nav_overview, R.id.nav_setup, R.id.nav_catalog, R.id.nav_play, R.id.nav_help};
    private static final int[] PAGES = {R.layout.page_overview, R.layout.page_setup, R.layout.page_catalog, R.layout.page_play, R.layout.page_help};
    private int selected;
    private boolean busy;
    private Snapshot snapshot;
    private String packageDraft = "";
    private String catalogMessage = "";

    private static final class Snapshot {
        DeviceCommands.Result catalog;
        String developer;
        String debugging;
        String selinux;
        boolean playAvailable;
        long memoryTotal;
        long memoryAvailable;
    }
    private interface Job { String run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        if (saved != null) {
            selected = saved.getInt("page", 0);
            packageDraft = saved.getString("package", "");
            catalogMessage = saved.getString("catalogMessage", "");
        }
        for (int i = 0; i < NAV.length; i++) {
            final int page = i;
            findViewById(NAV[i]).setOnClickListener(view -> showPage(page));
        }
        showPage(selected);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        saveDraft();
        out.putInt("page", selected);
        out.putString("package", packageDraft);
        out.putString("catalogMessage", catalogMessage);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void saveDraft() {
        EditText field = findViewById(R.id.package_name);
        if (field != null) packageDraft = field.getText().toString();
    }

    private void showPage(int page) {
        saveDraft();
        selected = Math.max(0, Math.min(page, PAGES.length - 1));
        FrameLayout container = findViewById(R.id.page);
        container.removeAllViews();
        getLayoutInflater().inflate(PAGES[selected], container, true);
        for (int i = 0; i < NAV.length; i++) {
            Button button = findViewById(NAV[i]);
            button.setBackgroundTintList(ColorStateList.valueOf(i == selected ? 0xFF69DFCD : 0xFF293C53));
            button.setTextColor(i == selected ? 0xFF0C1420 : 0xFFF1F5FA);
            button.setSelected(i == selected);
        }
        ((ScrollView) findViewById(R.id.content_scroll)).scrollTo(0, 0);
        bindPage();
        paintStatus();
        setBusy(busy);
    }

    private void on(int id, View.OnClickListener click) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(click);
    }

    private void bindPage() {
        on(R.id.refresh_status, view -> refresh());
        on(R.id.start_setup, view -> showPage(1));
        View starter = findViewById(R.id.starter_notice);
        if (starter != null) starter.setVisibility(BuildConfig.STARTER ? View.VISIBLE : View.GONE);
        on(R.id.open_storage, view -> openStorage());
        on(R.id.open_developer, view -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        on(R.id.open_settings, view -> open(new Intent(Settings.ACTION_SETTINGS)));
        on(R.id.open_wifi, view -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        on(R.id.open_about, view -> open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        on(R.id.allow_all, view -> confirmCatalog(true));
        on(R.id.restore_catalog, view -> confirmCatalog(false));
        EditText field = findViewById(R.id.package_name);
        if (field != null) field.setText(packageDraft);
        on(R.id.approve_package, view -> approvePackage());
        on(R.id.open_play, view -> PlayStoreActivity.open(this));
        on(R.id.toggle_shortcut, view -> toggleShortcut());
        on(R.id.play_to_catalog, view -> showPage(2));
        on(R.id.show_commands, view -> showCommands("PC fallback", manualCommands()));
        on(R.id.view_report, view -> showReport());
        on(R.id.toolbox_settings, view -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        setText(R.id.about_toolbox, "Version " + BuildConfig.VERSION_NAME + (BuildConfig.STARTER ? " · Starter edition" : " · Standard edition")
                + "\nNo ads, trackers, internet access, root commands, or background services. Changes happen only when you choose an action."
                + "\n\nThe tested firmware already had SELinux in Permissive mode. Toolbox never changes this setting. Firmware with different permissions may refuse direct catalog changes."
                + "\n\nUninstalling Toolbox does not restore the catalog. Use Restore catalog restriction first if desired.");
    }

    private boolean testedModel() {
        return "TC".equalsIgnoreCase(Build.MANUFACTURER) && "t88".equalsIgnoreCase(Build.MODEL) && Build.VERSION.SDK_INT == 29;
    }

    private Snapshot readSnapshot() {
        Snapshot result = new Snapshot();
        result.catalog = DeviceCommands.readCatalog();
        result.developer = setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        result.debugging = setting(Settings.Global.ADB_ENABLED);
        DeviceCommands.Result enforcement = DeviceCommands.run("/system/bin/getenforce");
        result.selinux = enforcement.ok() ? enforcement.output : "unavailable";
        result.playAvailable = PlayStoreActivity.available(this);
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) {
            manager.getMemoryInfo(memory);
            result.memoryTotal = memory.totalMem;
            result.memoryAvailable = memory.availMem;
        }
        return result;
    }

    private String setting(String name) {
        try {
            String value = Settings.Global.getString(getContentResolver(), name);
            return value == null ? "unset" : value;
        } catch (RuntimeException unavailable) { return "unavailable"; }
    }

    private String flagLabel(String value) {
        if ("1".equals(value)) return "On";
        if ("0".equals(value)) return "Off";
        return value + " (not a confirmed off state)";
    }

    private String catalogLabel() {
        if (snapshot == null) return "Checking catalog status…";
        if (!snapshot.catalog.ok()) return "Catalog status unavailable";
        String value = snapshot.catalog.output;
        if (DeviceCommands.isTrue(value)) return "Catalog bypass is enabled";
        if (DeviceCommands.isFalse(value)) return testedModel() ? "Catalog restriction is active" : "Catalog bypass is not enabled (firmware unverified)";
        return "Catalog value is unrecognized";
    }

    private void setText(int id, String value) {
        TextView view = findViewById(id);
        if (view != null) view.setText(value);
    }

    private void paintStatus() {
        setText(R.id.device_match, Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + (testedModel() ? "\nMatches the tested model; firmware can still differ." : "\nThis model or Android version has not been verified."));
        if (snapshot != null) {
            setText(R.id.status_summary, catalogLabel()
                    + "\nDeveloper mode: " + flagLabel(snapshot.developer)
                    + "\nUSB debugging: " + flagLabel(snapshot.debugging)
                    + "\nGoogle Play: " + (snapshot.playAvailable ? "Available" : "Missing or disabled")
                    + "\nMemory: " + snapshot.memoryAvailable / 1048576 + " MB available / " + snapshot.memoryTotal / 1048576 + " MB total");
            setText(R.id.developer_state, "Developer mode: " + flagLabel(snapshot.developer) + "\nUSB debugging: " + flagLabel(snapshot.debugging));
            setText(R.id.play_state, snapshot.playAvailable ? "Google Play is available" : "Google Play is missing or disabled");
        }
        setText(R.id.catalog_state, catalogLabel());
        setText(R.id.catalog_result, catalogMessage);
        View result = findViewById(R.id.catalog_result);
        if (result != null) result.setVisibility(catalogMessage.isEmpty() ? View.GONE : View.VISIBLE);
        boolean shortcut = shortcutEnabled();
        setText(R.id.toggle_shortcut, shortcut ? "Remove Toolbox’s Play Store shortcut" : "Add Play Store to the home screen");
        setText(R.id.shortcut_state, shortcut ? "Toolbox’s shortcut is enabled. It may appear on another home-screen page." : "This adds a separate entry that opens the real Play Store. Existing shortcuts are not changed.");
    }

    private void setBusy(boolean value) {
        busy = value;
        findViewById(R.id.busy).setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        int[] controls = {R.id.allow_all, R.id.restore_catalog, R.id.refresh_status};
        for (int id : controls) { View view = findViewById(id); if (view != null) view.setEnabled(!value); }
    }

    private void refresh() { execute(null, null); }

    private void execute(Job job, String fallbackCommand) {
        if (busy || worker.isShutdown()) return;
        setBusy(true);
        worker.execute(() -> {
            String message = null;
            String failure = null;
            try { if (job != null) message = job.run(); }
            catch (Exception error) { failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
            Snapshot latest = readSnapshot();
            final String success = message;
            final String problem = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                snapshot = latest;
                if (success != null) catalogMessage = success;
                if (problem != null) catalogMessage = "The requested change could not be verified.";
                paintStatus();
                setBusy(false);
                if (problem != null) showCommands("The device did not complete this change",
                        problem + (fallbackCommand == null ? "" : "\n\nUse this command from an authorized PC:\n\n" + fallbackCommand));
                else if (success != null) Toast.makeText(this, success, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void confirmCatalog(boolean allow) {
        String explanation = allow
                ? "This skips the RC29 catalog check for ALL installation sources, including Google Play and APK files. Android compatibility and signature checks remain. The setting can remain after reboot and after removing Toolbox."
                : "This turns the RC29 catalog restriction back on for new, unapproved packages. Installed apps and existing individual approvals stay in place.";
        if (!testedModel()) explanation += "\n\nThis device does not match the tested TC t88 / Android 10 model. The command may be unsupported.";
        new AlertDialog.Builder(this).setTitle(allow ? "Allow all app installs?" : "Restore the catalog restriction?")
                .setMessage(explanation).setNegativeButton("Cancel", null)
                .setPositiveButton(allow ? "Allow all apps" : "Restore restriction", (dialog, which) -> {
                    String expected = allow ? "1" : "0";
                    execute(() -> {
                        DeviceCommands.Result written = DeviceCommands.writeCatalog(allow);
                        DeviceCommands.Result read = DeviceCommands.readCatalog();
                        if (!written.ok() || !read.ok() || !expected.equals(read.output))
                            throw new IllegalStateException("The firmware did not confirm the change. " + written.output);
                        return allow ? "Verified: catalog bypass is enabled." : "Verified: catalog restriction is restored.";
                    }, "adb shell setprop " + DeviceCommands.CATALOG_PROPERTY + " " + expected);
                }).show();
    }

    private String enteredPackage() {
        saveDraft();
        String value = packageDraft.trim();
        if (!DeviceCommands.validPackage(value)) {
            EditText field = findViewById(R.id.package_name);
            if (field != null) field.setError("Enter a package name such as com.block.juggle");
            return null;
        }
        return value;
    }

    private void approvePackage() {
        String packageName = enteredPackage();
        if (packageName == null) return;
        showCommands("Approve one package from a PC",
                "Connect and authorize your computer, then use option 4 in Start-Windows.cmd. Or run these commands with Android Platform Tools."
                + "\n\nRead and save the previous value first:\nadb shell settings --user 0 get system " + packageName
                + "\n\nApprove the package:\nadb shell settings --user 0 put system " + packageName + " 1"
                + "\n\nTo undo, restore the previous value. If it was null, remove the new entry:\nadb shell settings --user 0 delete system " + packageName
                + "\n\nThese examples use Android user 0. The Windows helper detects the active user. With several devices, add -s DEVICE_SERIAL after adb."
                + "\n\nApproval does not install the app or authenticate its developer. No change has been made by displaying this command.");
    }

    private void openStorage() {
        Intent direct = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                .setClassName("com.android.settings", "com.android.settings.Settings$StorageDashboardActivity");
        if (!tryOpen(direct) && !tryOpen(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)))
            open(new Intent(Settings.ACTION_SETTINGS));
    }

    private boolean tryOpen(Intent intent) {
        try { startActivity(intent); return true; }
        catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }

    private void open(Intent intent) {
        if (!tryOpen(intent)) Toast.makeText(this, "This screen is unavailable or blocked on this firmware.", Toast.LENGTH_LONG).show();
    }

    private ComponentName shortcutComponent() { return new ComponentName(this, "com.rc29.toolbox.PlayStoreShortcut"); }
    private boolean shortcutEnabled() {
        return getPackageManager().getComponentEnabledSetting(shortcutComponent()) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
    private void toggleShortcut() {
        if (BuildConfig.STARTER) {
            new AlertDialog.Builder(this).setTitle("Use the standard Toolbox for this shortcut")
                    .setMessage("The starter is a temporary setup helper. Install the standard RC29 Toolbox APK after enabling app installs, then add its Play Store shortcut so it remains when you remove the starter.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        boolean enable = !shortcutEnabled();
        if (enable && !PlayStoreActivity.available(this)) {
            Toast.makeText(this, "Install or enable Google Play before adding its shortcut.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            getPackageManager().setComponentEnabledSetting(shortcutComponent(), enable
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            if (shortcutEnabled() != enable) throw new IllegalStateException("The shortcut state did not change.");
            paintStatus();
            Toast.makeText(this, enable ? "Play Store shortcut added. Return Home to find it." : "Toolbox’s Play Store shortcut removed.", Toast.LENGTH_LONG).show();
        } catch (RuntimeException failed) { showCommands("Shortcut could not be updated", failed.toString()); }
    }

    private String manualCommands() {
        return "Run these on your computer with Android Platform Tools after authorizing USB debugging."
                + "\n\nCheck the connected device:\nadb devices"
                + "\n\nRead catalog bypass:\nadb shell getprop " + DeviceCommands.CATALOG_PROPERTY
                + "\n\nAllow all app installs:\nadb shell setprop " + DeviceCommands.CATALOG_PROPERTY + " 1"
                + "\n\nRestore the catalog restriction:\nadb shell setprop " + DeviceCommands.CATALOG_PROPERTY + " 0"
                + "\n\nOpen the installed Play Store:\nadb shell am start -n com.android.vending/com.android.vending.AssetBrowserActivity"
                + "\n\nIf more than one device is connected, use adb -s DEVICE_SERIAL for each command.";
    }

    private String report() {
        String text = "RC29 Toolbox " + BuildConfig.VERSION_NAME + (BuildConfig.STARTER ? " (Starter)" : "")
                + "\nManufacturer: " + Build.MANUFACTURER + "\nModel: " + Build.MODEL
                + "\nAndroid: " + Build.VERSION.RELEASE + "\nAPI: " + Build.VERSION.SDK_INT
                + "\nABIs: " + TextUtils.join(", ", Build.SUPPORTED_ABIS)
                + "\nBuild display: " + Build.DISPLAY + "\nFingerprint: " + Build.FINGERPRINT
                + "\nToolbox UID: " + android.os.Process.myUid();
        if (snapshot != null) text += "\nCatalog bypass property: " + (snapshot.catalog.ok()
                ? (snapshot.catalog.output.isEmpty() ? "unset (default false on tested firmware)" : snapshot.catalog.output) : "unavailable")
                + "\nDeveloper mode: " + snapshot.developer + "\nUSB debugging: " + snapshot.debugging
                + "\nSELinux: " + snapshot.selinux + "\nGoogle Play launchable: " + snapshot.playAvailable;
        return text + "\n\nTested reference: TC t88, Android 10, T88-MIPI91-USER-20260727172621."
                + "\nOther firmware and reboot persistence require separate verification.";
    }

    private TextView dialogText(String value) {
        TextView text = new TextView(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        text.setTextSize(16);
        text.setText(value);
        text.setTextIsSelectable(true);
        return text;
    }
    private ScrollView scrollText(String value) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(dialogText(value));
        return scroll;
    }
    private void copy(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("RC29 Toolbox", value));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }
    private void showCommands(String title, String content) {
        new AlertDialog.Builder(this).setTitle(title).setView(scrollText(content))
                .setNegativeButton("Close", null).setPositiveButton("Copy", (d, w) -> copy(content)).show();
    }
    private void showReport() {
        String content = report();
        new AlertDialog.Builder(this).setTitle("Device report").setView(scrollText(content))
                .setNegativeButton("Close", null).setNeutralButton("Copy", (d, w) -> copy(content))
                .setPositiveButton("Share…", (d, w) -> {
                    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content);
                    open(Intent.createChooser(share, "Share device report"));
                }).show();
    }
}
