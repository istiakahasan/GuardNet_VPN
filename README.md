# WG Client

A minimal WireGuard VPN client for Android (Kotlin, Jetpack Compose, Material 3).

## Project structure

```
app/src/main/java/com/vpnproject/wgclient/
  MainActivity.kt        Entry point, hosts the Compose UI
  data/
    ConfigManager.kt     Validates, saves and loads the WireGuard config
    SecureStore.kt       AES-GCM encryption using a key held in the Android Keystore
  vpn/
    VpnViewModel.kt      Tunnel control, connection state, traffic stats
  ui/
    MainScreen.kt        Main screen, onboarding and dialogs
    theme/               Color, Type and Theme
```

## Secrets policy

- No keys, passwords or server details are hard-coded in the source.
- The user's WireGuard config (including its private key) is imported at runtime and stored
  encrypted (AES-256-GCM). The encryption key lives in the Android Keystore and cannot be exported.
- App backups are disabled and the config is never logged.
- Release signing credentials are not in the repo. Provide them via either:
  - `keystore.properties` in the project root (git-ignored). See `keystore.properties.example`.
  - Environment variables: `WG_STORE_FILE`, `WG_STORE_PASSWORD`, `WG_KEY_ALIAS`, `WG_KEY_PASSWORD`.
- Keep the keystore file outside the project folder and back it up. It is required for every update.

## Build

```
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Requires JDK 17 and the Android SDK (set `sdk.dir` in `local.properties`).
