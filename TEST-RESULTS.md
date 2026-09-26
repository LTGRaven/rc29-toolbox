# RC29 Toolbox 0.1.1 beta — verification

Test date: 4 September 2026. One physical LAMTTO RC29 / TC t88 was used. This is not a certification of other units or firmware.

## Reference device

- Manufacturer/model: TC / t88.
- Android: 10, API 29.
- Firmware label supplied by owner: `T88-MIPI91-USER-20260727172621`.
- Build: `QP1A.191105.004 dev-keys`.
- Fingerprint: `alps/Android/Android:10/QP1A.191105.004/826:user/dev-keys`.
- SELinux was already `Permissive`; no SELinux change or root access was used.

## Verified

- Both standard and starter release variants built successfully with Gradle 8.11.1, Android Gradle Plugin 8.9.2, JDK 21 and SDK 35.
- Both final APK signatures passed Android `apksigner` verification with minimum API 21. Package metadata confirms minSdk 21, targetSdk 35, version code 2, and version name `0.1.1-beta`.
- Final APKs contain no requested Android permissions or native libraries and are not debuggable. There are no third-party runtime dependencies.
- The standard edition installed and ran as an ordinary application user, without root or privileged settings permissions.
- The app's **Restore catalog restriction** button set the vendor property to `0`. A fresh, unapproved test APK was then rejected with `INSTALL_FAILED_INVALID_APK` / `Invalid package!`.
- The app's **Allow all app installs** button set the property to `1`. The identical test APK then installed successfully, with its individual approval still absent. The test APK was removed afterward.
- The above catalog test used the app's buttons and confirmation dialogs. ADB was used to observe the property and attempt the probe installation; ADB did not perform the catalog toggle during this test.
- **Open Android Storage** opened `com.android.settings.Settings$StorageDashboardActivity`.
- The standard edition enabled and disabled its Play Store launcher alias. Launching that alias opened the existing `com.android.vending` Play Store activity. The device's older Play Store shortcut was left in place.
- The report preview showed the expected Android/build/setup fields without device serial, accounts, network names, or logs. No report was sent to another person or service during testing.
- Package-name validation checks passed, including rejection of malformed names, newlines and shell-like input. Catalog-value parsing checks passed.
- The final individual-approval screen displayed the expected read/approve/undo PC commands for `com.block.juggle` without requesting permission or changing an approval.
- The Windows helper parsed successfully, reported device status under Windows PowerShell 5 using the supplied launcher's process-local execution-policy option, and installed/opened the final standard APK successfully.
- The 0.1.1 helper was updated after a report from firmware `T88-MIPI91-USER-20260331174849`. It now grants and verifies the standard package's individual approval before every installation attempt, including when the master bypass property already reads as enabled. This compatibility path has not yet been tested on that reporting device.

The app-level individual-approval experiment was rejected by this firmware even after Modify system settings permission was granted. The shipped app therefore offers that operation through PC instructions only and declares no settings permission. The temporary test permission was reset and no test approval was created.

## Limits of this beta

- The newly signed starter APK was built and its signature checked, but its first installation on a factory/catalog-locked device has not been retested. An older, differently signed diagnostic helper already occupies `com.sirius` on the test unit and was deliberately preserved. The starter route is based on that package identity having worked for the earlier helper; other firmware may differ.
- The Storage eight-tap developer-mode route was established earlier on this device. It was not repeated by disabling developer mode during Toolbox testing.
- The main enable/restore cycle was tested before the final removal of the unused individual-approval permission and UI. The catalog implementation was unchanged in the final build, which was installed successfully afterward.
- No reboot, factory reset, firmware update, or test on another RC29 was performed for this release. Persistence across reboot/update is not separately confirmed.
- Play Store account setup, Google services installation, certification, paid-app licensing, DRM, and universal app compatibility are outside the app's functionality. A catalog bypass cannot make an incompatible app work.
- Other Android versions, portrait layouts, multiple-device selection, and Windows helper approval/install-failure rollback paths have not all been tested on physical devices. Their source is included for review.

## Release signing identity

Both editions use this certificate SHA-256 fingerprint:

```text
fffa0a2e0e64c8704ddba1797f43528e37d46d675a336cdfa47b6d5ef33f311e
```

The private key is excluded from every shared artifact. `SHA256SUMS.txt` records the final file hashes for this release.
