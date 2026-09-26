# RC29 Toolbox 0.1.1 beta

A community app for LAMTTO RC29 / TC t88 owners, developed with help from OpenAI's Codex.

- Guided access to the hidden developer setup route.
- Enable or restore the vendor app-catalog restriction, with result verification.
- Open the existing Google Play Store and add a home-screen shortcut.
- Preview, copy, and share device information.
- Windows helper for installation and individual package approvals.

**Download `RC29-Toolbox-0.1.1-beta.zip` for the complete kit and read `README.md` inside before installing.** The separate APK downloads are also available for convenience. GitHub's automatically generated source archives do not include the signed APKs.

Use **RC29-Toolbox.apk** on an RC29 that already accepts apps, or with the Windows helper. The optional **RC29-Toolbox-Starter.apk** uses the `com.sirius` package identity that worked for the original diagnostic helper; it contains no SiriusXM code or signature. Existing SiriusXM/diagnostic installations can cause a signature conflict.

Tested on one TC t88 running Android 10, firmware `T88-MIPI91-USER-20260727172621`, without root. Enabling/restoring the catalog restriction and opening Play Store were verified on that unit. The newly signed Starter APK still needs an initial-install test on another locked unit. Other firmware and reboot persistence are not yet verified.

The catalog bypass does not guarantee Android compatibility or disable normal signature checks. The app requests no Android permissions and does not upload reports automatically. To send feedback, copy your report from **Help & report** into a GitHub issue using the device-report template.

### Compatibility fix

Reports from firmware `T88-MIPI91-USER-20260331174849` show that the global bypass property can read as enabled while the installer continues enforcing per-package approvals. The Windows helper now always approves `com.rc29.toolbox` before installing the standard APK, even when the global property already reads as enabled. The app also explains this firmware difference instead of treating the property value as proof that every installation source is unlocked.

For that firmware, use the Starter to reach Developer Options and enable USB debugging. Connect the RC29 to Windows, open `Start-Windows.cmd`, and choose option 1. After the standard Toolbox installs, option 4 can approve other package IDs individually when the master bypass is ignored.

See `TEST-RESULTS.md` for details and `SHA256SUMS.txt` for the release file hashes.
