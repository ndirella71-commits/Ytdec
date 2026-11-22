# NovaTube — YouTube for Android TV (InnerTube)

A 10-foot YouTube client for Android TV / Google TV boxes, built directly on Google's InnerTube API
(`youtubei/v1`) — the same private API that the `MohammadKobirShah/InnerTube-API` repo wraps in Python.
No third-party server in between, no API key of your own, no ad slots requested.

## Install

```bash
adb connect <tv-ip>:5555
adb install -r dist/NovaTube-TV-1.0-debug.apk
```

Or copy the APK to the device and open it with a file manager / `Downloader` app.

- `minSdk 24`, `targetSdk 34` — runs on Android TV 7.0 and newer (TV 7, 8, 9+), arm/x86.
- The app registers both `LEANBACK_LAUNCHER` (TV home row) and `LAUNCHER` (for phone testing).

## Screens

| Screen | What it does |
| --- | --- |
| Home | Hero banner + horizontally scrolling shelves from `browse FEwhat_to_watch`, with trending/search fallbacks so it is never empty |
| Search | D-pad on-screen keyboard, results grid, infinite scroll |
| Subscriptions / Library | `FEsubscriptions` and `FEmy_youtube` once signed in |
| Settings | Google sign-in, OAuth client setup, about |
| Player | ExoPlayer + TV controls (OK play/pause, ←/→ seek 10 s, BACK exit), auto-plays the next item in the row |

Controls follow the YouTube TV focus model: a white focus ring with a slight scale-up, focus-driven
row scrolling, and left rail navigation.

## Sign-in (optional)

Google refuses OAuth inside embedded WebViews, so sign-in uses the standard TV device flow — the same
pattern the real YouTube TV app uses:

1. Google Cloud Console → create an OAuth client of type **TVs and Limited Input devices**.
2. Enable the **YouTube Data API v3** for that project.
3. In the app: Settings → *Google OAuth client* → paste the **client id** and **client secret**.
4. Settings → *Sign in with Google* → a code appears; open `google.com/device` (the app offers an
   in-app browser button) or enter the code on your phone.

Tokens stay on the device in app-private storage. Anonymous browsing, search and playback need no account.

## Ads

The client never requests ad placements or ad breaks and has no ad SDK; playback uses the raw stream
URLs YouTube returns, so there is nothing to render an ad into.

## Build from source

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # parser test against a captured real InnerTube payload
```

Requires JDK 17 and Android SDK 34 (`local.properties` → `sdk.dir`).

## Continuous integration

`.github/workflows/build-apk.yml` runs on every push and pull request (and can be started by hand
from the Actions tab). It runs the parser unit test, builds the debug APK and uploads it as the
`NovaTube-TV-debug-apk` artifact, which installs on Android TV 7.0+.

## Known limitations

- YouTube blocks datacenter IPs, so playback could not be validated in the build environment; it is
  built for residential/TV networks. Client versions live in `data/InnerTube.kt` (`object Clients`) and
  are the strings to bump when YouTube retires a client.
- Cipher-protected (`signatureCipher`) stream formats are skipped rather than decrypted; the client
  tries ANDROID → IOS → TV → WEB and uses the first response with plain URLs.
- Branding is original. YouTube's logos, fonts and assets are trademarks of Google and are not included.
