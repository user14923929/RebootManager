# Reboot Manager

Advanced power menu for Android: Restart, Recovery, Bootloader, Fastboot, FastbootD, Shutdown.
Executes through **Root**, **USB ADB** or **Wireless ADB**, with an **Auto** mode (Root → USB ADB → Wireless ADB → unavailable).

Kotlin · Jetpack Compose · Material 3 · Gradle Kotlin DSL · GPL-3.0 · no ads, no cloud.

## Build

```bash
gradle wrapper --gradle-version 8.9   # once, to generate ./gradlew
./gradlew assembleDebug
```

JDK 17 is required by AGP; `gradle.properties` points `org.gradle.java.home` at `/usr/lib/jvm/java-17-openjdk`.
Adjust it (or remove the line) on other machines. Library versions in `gradle/libs.versions.toml` may be bumped.

## Architecture

```
ui (Compose)  ──►  MainViewModel  ──►  RebootManager
                                         ├── CapabilityDetector   (SDK_INT, root, ADB, Wireless ADB, modes)
                                         ├── RebootCommandProvider (RebootCommand: mode → shell candidates)
                                         └── CommandExecutor
                                               ├── RootExecutor         su -c <cmd>
                                               ├── AdbExecutor          adbd @ 127.0.0.1
                                               └── WirelessAdbExecutor  adbd @ <Wi-Fi IP>, Android 11+ only
adb/: AdbClient (ADB wire protocol over TCP), AdbKeyStore (app-private RSA key), AdbEndpointResolver
```

The UI never runs shell commands; every reboot goes through `RebootManager.reboot()` after an explicit tap.

## Method states

`Unsupported` → `Supported but not connected` → `Connected` → `Ready`

Android 11+ (API 30) only makes Wireless ADB *supported*. It becomes *Connected* when adbd answers on the Wi-Fi address,
and *Ready* when adbd has accepted this app's key. Auto only uses `Ready` methods.

## How ADB works here (honest version)

A normal app has no ADB privileges. Reboot Manager is an ADB **client** that talks to the device's own adbd over TCP:

1. Enable USB debugging, connect a computer once and run `adb tcpip 5555`.
2. Open the app, tap **Authorize** and accept the "Allow debugging?" dialog.

Not implemented: Android 11+ "Wireless debugging" with pairing codes (TLS). It would be another transport behind `AdbEndpoint`.

## Releases (CI/CD)

`.github/workflows/release.yml` builds and publishes a **debug APK** only when a `v*` tag is pushed:

```bash
git tag v1.0.0
git push origin v1.0.0
```

- `versionName` comes from the tag (`v1.0.0` -> `1.0.0`), `versionCode` from the workflow run number.
- Tags containing `-` (e.g. `v1.0.0-rc1`) are published as pre-releases.
- The APK is signed with the runner's auto-generated debug key, which differs between runs:
  installing a newer release over an older one requires uninstalling the old one first.
