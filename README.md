# MITV — IPTV Android App

Kotlin + Jetpack Compose + ExoPlayer(Media3) IPTV app. Supports M3U, M3U8,
Xtream Codes, and YouTube links. Netflix-style UI, Firebase backend, Google
Login, push notifications, and custom user-tracking (location/device/time)
in Realtime Database.

## ⚠️ Required setup before this builds successfully

### 1. Firebase project
- Create/use a Firebase project (your existing `ramadan-2385b` project works).
- Add an Android app with package name **`com.mitv.master`**.
- Download the real `google-services.json` and replace `app/google-services.json`
  (currently a placeholder — the app will crash on Firebase calls until replaced).
- Enable in Firebase Console:
  - **Authentication → Google** sign-in provider
  - **Realtime Database** (start in test mode, then lock down with rules)
  - **Cloud Messaging** (for push notifications)

### 2. Google Sign-In Web Client ID
- In `MainActivity.kt`, replace:
  ```kotlin
  private val webClientId = "YOUR_FIREBASE_WEB_CLIENT_ID"
  ```
  with the **Web client ID** (OAuth 2.0 Client ID, type "Web application")
  found in Firebase Console → Project Settings → your app, or in
  Google Cloud Console → Credentials.

### 3. GitHub Actions secret (so CI can build with real Firebase config)
- Base64-encode your real `google-services.json`:
  ```bash
  base64 -w 0 google-services.json > encoded.txt
  ```
- In your GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**
  - Name: `GOOGLE_SERVICES_JSON_B64`
  - Value: paste contents of `encoded.txt`

Without this secret, CI builds with the placeholder file and Firebase features
will not work in the built APK (app will still build/launch, since Firebase
calls are wrapped/guarded where reasonable, but login/tracking/push won't function).

### 4. Realtime Database structure (reference)
```
/channels/{channelId}                — admin-curated shared channel list
/playlists/{uid}/{playlistId}        — per-user saved M3U/Xtream playlists
/app_config/update                   — { latestVersionCode, forceUpdate, downloadUrl, updateMessage }
/user_tracking/{uid}/sessions/{id}   — location, device, timestamps, current channel
```

## Build & release flow

1. Push code to the `main` branch (or trigger manually via **Actions → Build and
   Release MITV APK → Run workflow**).
2. GitHub Actions:
   - Regenerates the Gradle wrapper jar (not committed to this repo)
   - Restores the real `google-services.json` from the secret
   - Runs `./gradlew assembleDebug` and `./gradlew assembleRelease`
   - Uploads both APKs as build artifacts
   - Creates a GitHub Release with the APKs attached
3. Download the APK from the release page or the workflow run's Artifacts section.

> Note: the release APK is **unsigned**. For a Play Store-ready signed build,
> add a signing config (`signingConfigs` in `app/build.gradle.kts`) backed by
> GitHub secrets for your keystore, alias, and passwords.

## Project structure

```
app/src/main/java/com/mitv/master/
├── data/
│   ├── model/        Channel, Playlist, XtreamConfig, UserSession
│   ├── parser/        M3uParser
│   ├── remote/        XtreamApiService, RetrofitProvider
│   └── repository/    FirebaseRepository, UserTrackingRepository
├── di/                Hilt AppModule
├── service/           MitvFirebaseMessagingService (push notifications)
├── ui/
│   ├── navigation/     MitvNavGraph
│   ├── screens/        splash / login / home / player
│   └── theme/          MITV dark+gold Compose theme
├── util/              PlayerFactory (ExoPlayer tuning), YoutubeResolver
├── viewmodel/          HomeViewModel, PlayerViewModel, LoginViewModel
├── MainActivity.kt
└── MitvApplication.kt
```

## Still to implement (next iteration)
- Xtream Codes UI flow (login form → fetch categories/streams → merge into Channel list)
- Audio track selection UI (backend hook already in PlayerViewModel.selectAudioTrack)
- Favorites persistence (Room DB entities scaffolded via dependency, DAOs not yet written)
- Admin panel view for `/user_tracking` (could reuse your existing MITV Network Portal PWA)
- APK signing config for Play Store-ready release builds
