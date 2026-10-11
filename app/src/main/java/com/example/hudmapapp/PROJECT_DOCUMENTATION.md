# HudMapApp Documentation

**Last reviewed:** 2026-09-22
**Application ID:** `com.example.hudmapapp`

## 1. What HudMapApp is

HudMapApp is an Android app progressing toward a windshield-style navigation
HUD. It is not a replacement Google Maps UI: Google services provide the map,
place data, route planning, and navigation machinery; HudMapApp owns the
experience, app-state transformation, and the custom HUD presentation.

```text
Google Maps (via the Navigation SDK's bundled Maps classes)
    → renders the interactive Home map

Google Places SDK
    → search, place details, photos, phone numbers

Routes API (plain HTTPS)
    → route planning with alternative routes

Navigation SDK for Android
    → headless turn-by-turn guidance sessions

App-owned navigation state
    → converted guidance + progress, safe for the UI

Custom Compose HUD
    → landscape-only windshield display
```

## 2. Technology and build profile

| Area | Implementation |
|---|---|
| Module structure | Single application module `:app` |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Navigation | Navigation Compose, serializable typed routes |
| Min / compile SDK | API 26 / API 37 |
| Map rendering | The Navigation SDK's bundled `MapView` (see note below) |
| Place search / POI data | Places SDK for Android 5.3.0 |
| Route calculation | Routes API over plain HTTPS (`computeRoutes`) |
| Navigation | Navigation SDK for Android 7.6.1 (headless `Navigator`) |
| Turn-by-turn data | `TurnByTurnService` (SDK `NavInfo` feed) |
| Location | Play Services Fused Location Provider |
| DI | Manual construction, no framework |

**Important map-rendering fact:** the Navigation SDK **replaces** the Maps
SDK — the two cannot coexist in one APK (`com.google.android.gms.maps.*`
duplicate classes; `play-services-maps` is globally excluded in
`app/build.gradle.kts`). The bundled `MapView` behaves exactly like a normal
Google map, so Home renders through it directly. Maps Compose is not used.

**Navigation SDK provisioning:** the API key must have the
*Navigation SDK for Android* API enabled in Google Cloud, plus active
billing. With an unprovisioned key the SDK renders only the Google logo
(no tiles), the terms dialog degrades to a single "Got it" button, and
terms acceptance never persists. The SDK and API key state are fully
validated on real usage, not just at init.

## 3. Local setup

Prerequisites: Android SDK 37, AGP/Kotlin/Gradle with Java 11 toolchain,
a Google Cloud project with **Maps SDK for Android, Places SDK,
Navigation SDK** enabled, and `MAPS_API_KEY` in `local.properties`:

```properties
MAPS_API_KEY=your_restricted_google_api_key
```

The key is injected into `BuildConfig.MAPS_API_KEY` and the
`com.google.android.geo.API_KEY` manifest placeholder. Restrict the key by
application ID and SHA-1 fingerprint for both debug and release builds.

Common commands:

```text
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug
```

The app builds without a key but Google features fail at runtime.

## 4. Screens and user flows

### Splash → Introduction → Home

Splash, three static introduction pages, then Home. Location permission and
the Navigation SDK terms flow happen on first Home entry.

### Home — the real screen

Renders the map full-screen (bundled `MapView`) behind Compose overlays:

- **Search bar + results** (Places autocomplete with 300 ms debounce,
  location bias). Find keyboard is not raised automatically.
- **POI tap sheet** (`PoiDetailsSheet`): name, rating, address, phone
  number, photo gallery (Places `FetchPhotoRequest`), and a Navigate button
  that starts route calculation. POI taps are ignored while a destination is
  selected or route previews are open, so map exploration never wipes the
  selection.
- **Route preview**: `DestinationBottomSheet` (confirm) → `RoutePreviewSheet`
  (choose among alternative routes). Dismissing the preview sheet only hides
  it; a bottom "Choose route" pill reopens it and a clear button removes the
  selection explicitly.
- **Map polylines**: the selected route is drawn as ~24 chunks colored
  along `MainGradient` (sky blue → purple → pink); alternatives are gray.
  A click gate de-duplicates overlapping polyline taps so exactly one route
  is selected.
- **Map controls**: layers (satellite toggle), GPS/my-location (colors by
  fix availability, recenters on tap), dark-mode (applies
  `res/raw/map_style_dark.json`; satellite and dark style are mutually
  exclusive). Active toggles turn the button primary-colored.
- **Map loading placeholder**: spinner + status line shown until the
  navigator is `Ready` (terms/permission/init/error states included).

### HUD (landscape-only, headless guidance)

While on the HUD screen the device forces `SENSOR_LANDSCAPE` and sets
screen brightness to full; both restore on exit. Layout, left to right:
remaining distance to the current maneuver, the maneuver glyph + instruction
with a small next-maneuver preview beside the distance, and ETA / remaining
time on the right. Values come from the live `NavigationState` flow.

- **Maneuver glyphs**: rotating arrow for standard turns; for roundabouts a
  drawn ring glyph with the exit arm at the counterclockwise exit position
  (exit 1 ≈ upper right, straight through = up, in 60° steps), driven by the
  SDK's roundabout turn number. `ROUNDABOUT_EXIT_*` SDK maneuvers map to
  `ManeuverType.ROUNDABOUT_EXIT`.
- **Mirroring**: the "Mirror" pill flips the guidance layer in place
  (`scaleX = -1`, close/mirror controls stay unmirrored). There is no
  separate mirrored route; `MirroredHUDScreen` was removed.
- **No map view on the HUD** — it is a headless parameters screen on black.

## 5. Architecture

```
Google SDKs / APIs  → raw data (tiles, place JSON, routes HTTP, Navigator)
data/  + navigation/  → immutable app-owned state (Destination, RoutePreview,
                        NavigationState, GuidanceSnapshot)
ui/                   → Compose pixels reading that state only
```

Key objects:

| File | Role |
|---|---|
| `HudMapApplication` | Sets the Google API key once and creates the shared `NavigationManager` (navigator init starts in `Application.onCreate`; retries when location permission lands). |
| `navigation/NavigationManager.kt` | One-shot `getNavigator` lifecycle: `Uninitialized → Initializing → Ready/Error/TermsNotAccepted`; `LOCATION_PERMISSION_MISSING` resets so init retries after the grant. Logs transitions under tag `HudMapNav`. |
| `navigation/NavigationSessionCoordinator.kt` | Session `ViewModel`: `Idle → Starting → Active → … → Arrived/Error`, owns SDK listeners, the 2 s guidance feed poll, background-grace pause policy. |
| `navigation/NavigatorAdapter.kt` | `SdkNavigatorAdapter` wraps `Navigator`: `setDestinations` (placeId waypoint, `CustomRoutesOptions` route token), guidance feed, `registerServiceForNavUpdates`, `getTimeAndDistanceList` fallback totals. |
| `navigation/TurnByTurnService.kt` | Messenger service bound via `registerServiceForNavUpdates`; decodes `MSG_NAV_INFO` bundles with `TurnByTurnManager` into the `TurnByTurnNavStore`. Declared in the manifest, `exported=false`. |
| `navigation/TurnByTurnMapper.kt` | Maps the SDK's `NavInfo` (current step, next step, distances) into `GuidanceSnapshot`. |
| `navigation/GuidanceMapper.kt` | `GuidanceSnapshot → NavigationState`; SDK `Maneuver` enum names → `ManeuverType`; ETA = now + remaining. |
| `navigation/NavigationState.kt` | `ManeuverType` (18 values incl. `ROUNDABOUT_EXIT`), `ManeuverInfo`, `NavigationProgress`, `NavigationState`. |
| `ui/screens/homeScreen/HomeMapView.kt` | Bundled `MapView` + polylines (gradient route, click gate), my-location, camera animations, satellite/dark styles, POI clicks. |
| `ui/screens/hudScreen/HUDScreen.kt` | Landscape HUD: distance / glyph+instruction / ETA+remaining, mirror toggle, full brightness, roundabout glyphs. |
| `ui/screens/hudScreen/HudSessionContent.kt` | Session-state switch shared by the HUD: Idle/Starting/Guidance/Interrupted/Arrived/Error. |
| `data/repository/DestinationRepository.kt` | Places autocomplete + `fetchPlaceDetails` + `fetchPoiDetails` (rating, phone, photos). |
| `data/repository/RoutePlanningRepository.kt` | Routes API HTTPS client, polyline decoding, index-unique route ids (`route-0`…), error taxonomy. |
| `location/…` | Fused location stack: permission handling, shared update provider, typed fix model. |

## 6. Navigation data pipeline

```
NavigationApi.getNavigator (Application.onCreate)
        │ Ready
        ▼
NavigationSessionCoordinator.startNavigation(dest, route)
        │ setDestinations → startGuidance → registerServiceForNavUpdates
        ▼
TurnByTurnService (bound by the SDK)
        │ NavInfo: current step, next steps, distances
        ▼
TurnByTurnNavStore  ──(2 s poll)──►  GuidanceSnapshot
        ▼
GuidanceMapper → NavigationState flow
        ▼
Home (session banner) and HUD (landscape guidance screen)
```

Notes:

- Registration uses `ApplicationId` (not the service's Java package) and
  requires `generatedStepImagesType` + `displayMetrics` on the options
  builder.
- Turn-by-turn `NavInfo` arrives through the bound, in-process service;
  when it is absent the coordinator falls back to totals-only snapshots
  (remaining distance/duration/ETA), which is why arrival, off-route, and
  voice guidance work even if maneuver data is missing.

## 7. Rerouting and arrival

Off-route, rerouting, interruption (GPS/network/background beyond the 30 s
grace), arrival, and error states are all modeled in
`NavigationSessionState` with user-facing strings in `userMessageFor` and
recovery actions in `recoveryActionFor`. Raw SDK text never reaches the
driver. Backgrounding pauses the session (no foreground service policy);
foreground auto-resumes.

## 8. Testing

Unit tests live in `app/src/test` (route parsing, preview coordinator,
guidance mapping, session state machine, HUD presentation). Compose
instrumented tests exist for the sheets/banners (local emulator only).
Run: `./gradlew testDebugUnitTest`.

## 9. Known limitations

- This is a single-activity Compose app; the Settings screen is a stub.
- Turn-by-turn maneuver delivery depends on the bound TBT service — if it
  fails to register (see logcat `HudMapNav`), the HUD shows totals only.
- The background policy pauses sessions rather than running a foreground
  navigation service.
- The API key must be restricted per build type (debug vs release SHA-1);
  a mismatching key silently degrades Navigation SDK behavior.

## 10. References

- Navigation SDK: https://developers.google.com/maps/documentation/navigation/android-sdk
- Routes API: https://developers.google.com/maps/documentation/routes
- Places SDK: https://developers.google.com/maps/documentation/places/android-sdk
