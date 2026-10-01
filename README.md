# Reboot Manager

Advanced power menu for Android: Restart, Recovery, Bootloader, FastbootD, Shutdown.
Executes through **Root**, **USB ADB** or **Wireless debugging (TLS)**, with an **Auto** mode
(Root → USB ADB → Wireless debugging → unavailable).

Package: `org.user14923929.rebootmanager`. Kotlin · Jetpack Compose · Material 3 · Gradle Kotlin DSL · GPL-3.0 · no ads, no cloud.

A standalone "Fastboot" mode was removed in v1.0.1: on most devices `reboot bootloader` is the only
command involved, and on some OEM devices (notably Samsung) the same physical mode is called
**Odin Mode**, so a button literally labeled "Fastboot" promised something device-specific that
the command itself doesn't guarantee. Bootloader mode is still fastboot-capable once you're there;
FastbootD (userspace fastboot, `reboot fastboot`) is unaffected and stays separate.

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
                                         ├── CapabilityDetector   (SDK_INT, root, ADB, Wireless debugging, modes)
                                         ├── RebootCommandProvider (RebootCommand: mode → shell candidates)
                                         └── CommandExecutor
                                               ├── RootExecutor         su -c <cmd>
                                               ├── AdbExecutor          adbd @ 127.0.0.1 (USB ADB, legacy TCP)
                                               └── WirelessAdbExecutor  adbd @ <Wi-Fi IP> (Wireless debugging, TLS), Android 11+ only
adb/:     AdbClient (legacy ADB-over-TCP wire protocol), AdbKeyStore (app-private RSA key), AdbEndpointResolver
adb/tls/: TlsIdentity (app-private RSA key + self-signed cert), AppAdbConnectionManager
          (libadb-android glue), WirelessDebuggingClient (pair + connect + shell)
```

The UI never runs shell commands; every reboot goes through `RebootManager.reboot()` after an explicit tap.

## Method states

`Unsupported` → `Supported but not connected` → `Connected` → `Ready`

Android 11+ (API 30) only makes Wireless debugging *supported*. It becomes *Connected* when adbd answers
on the configured port, and *Ready* once this app has been paired. Auto only uses `Ready` methods.

## How ADB works here (honest version)

A normal app has no ADB privileges. Reboot Manager is an ADB **client**, and uses two different
transports depending on the method, because they're genuinely different protocols on-device:

**USB ADB** — the legacy `adb tcpip` transport, hand-rolled in `adb/AdbClient.kt` (no library):

1. Enable USB debugging, connect a computer once and run `adb tcpip 5555`.
2. Open the app, tap **Authorize** and accept the "Allow debugging?" dialog.

**Wireless debugging** — Android 11+'s TLS transport with pairing codes, via the
[libadb-android](https://github.com/MuntashirAkon/libadb-android) library (no computer needed):

1. On the device: Settings > Developer options > Wireless debugging > enable it, then
   **Pair device with pairing code**. Note the pairing port and 6-digit code shown there.
2. In the app: Settings > **Pair device**, enter that port and code, tap Pair. adbd now trusts
   this app's certificate, the same way it trusts a paired computer.

There's no connection port to configure: `AbsAdbConnectionManager.autoConnect()` discovers adbd's
current wireless-debugging port itself via mDNS/NSD on every connection (that port changes each
time Wireless debugging is toggled), and pairing always talks to `127.0.0.1` — wireless debugging
is reached over loopback, not the device's Wi-Fi IP, since the app and adbd are on the same device.

Each transport has its own identity (RSA key + certificate), so authorizing one does not authorize
the other.

## Verified against a real app

`adb/tls/` originally guessed at `libadb-android`'s package and API from its README and did not
compile (wrong package: it's `io.github.muntashirakon.adb`, not `com.MuntashirAkon.libadb` — that
only names where JitPack hosts the artifact). It was rewritten against
[github.com/sam1am/anyapk](https://github.com/sam1am/anyapk), a real, building app using the same
library version, confirming the package name, the `3.1.0` version pin, the required
`conscrypt-android` and `sun-security-android` dependencies, and the `autoConnect` / `pair` /
`openStream` calls used here. Still worth knowing before you build:

- **Version drift.** `libadb-android:3.1.0` was current as of this writing; check
  https://jitpack.io/#MuntashirAkon/libadb-android if a newer tag exists.
- **No NSD permission testing.** `AndroidManifest.xml` declares `ACCESS_NETWORK_STATE` for
  `autoConnect`'s mDNS discovery; if pairing or connecting fails with a permission-looking error,
  `CHANGE_WIFI_MULTICAST_STATE` may also be needed — the reference app didn't need to request it
  explicitly, but it also ships more permissions overall (notifications, foreground service) than
  this app does.

## Releases (CI/CD)

Requires `gradlew` and `gradle/wrapper/` to be committed.

`.github/workflows/release.yml` builds and publishes a **debug APK** only when a `v*` tag is pushed:

```bash
git tag v1.0.0
git push origin v1.0.0
```

- `versionName` comes from the tag (`v1.0.0` -> `1.0.0`), `versionCode` from the workflow run number.
- Tags containing `-` (e.g. `v1.0.0-rc1`) are published as pre-releases.
- The APK is signed with the runner's auto-generated debug key, which differs between runs:
  installing a newer release over an older one requires uninstalling the old one first.
