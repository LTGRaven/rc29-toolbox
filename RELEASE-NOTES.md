# RC29 Toolbox 0.1.2 beta

A community app for LAMTTO RC29 / TC t88 owners, developed with help from OpenAI's Codex.

- Guided access to the hidden developer setup route.
- Enable or restore the vendor app-catalog restriction, with result verification.
- Open the existing Google Play Store and add a home-screen shortcut.
- Preview, copy, and share device information.
- Windows helper for installation and individual package approvals.

**Download `RC29-Toolbox-0.1.2-beta.zip` for the complete kit and read `README.md` inside before installing.** The separate APK downloads are also available for convenience. GitHub's automatically generated source archives do not include the signed APKs.

Use **RC29-Toolbox.apk** on an RC29 that already accepts apps, or with the Windows helper. For a fully locked unit, try **RC29-Toolbox-Bootstrap.apk**, enable all app installs, install the standard APK, and remove the Bootstrap.

Tested on one TC t88 running Android 10, firmware `T88-MIPI91-USER-20260727172621`, without root. Enabling/restoring the catalog restriction and opening Play Store were verified on that unit. The new Bootstrap APK still needs an initial-install test on another locked unit. Other firmware and reboot persistence are not yet verified.

The catalog bypass does not guarantee Android compatibility or disable normal signature checks. The app requests no Android permissions and does not upload reports automatically. To send feedback, copy your report from **Help & report** into a GitHub issue using the device-report template.

### New universal bootstrap route

A second report from firmware `T88-MIPI91-USER-20260727172621` confirmed that the earlier `com.sirius` Starter is not accepted on every unit. Firmware inspection shows why: `com.sirius` was only an individual approval on the original RC29. It is not one of the installer's built-in exceptions.

The new Bootstrap uses `com.github.metacubex.clash.meta`, one of two package names hardcoded as installable in this firmware's `services.jar`. It contains only Toolbox code, requests no permissions, and does not contain or use the third-party app's code or signature. A genuine app with that package name will cause Android's normal signature-conflict rejection; do not remove existing app data to work around that.

### Earlier-firmware compatibility

Reports from firmware `T88-MIPI91-USER-20260331174849` show that the global bypass property can read as enabled while the installer continues enforcing per-package approvals. The Windows helper now always approves `com.rc29.toolbox` before installing the standard APK, even when the global property already reads as enabled. The app also explains this firmware difference instead of treating the property value as proof that every installation source is unlocked.

For that firmware, use the Starter to reach Developer Options and enable USB debugging. Connect the RC29 to Windows, open `Start-Windows.cmd`, and choose option 1. After the standard Toolbox installs, option 4 can approve other package IDs individually when the master bypass is ignored.

See `TEST-RESULTS.md` for details and `SHA256SUMS.txt` for the release file hashes.
