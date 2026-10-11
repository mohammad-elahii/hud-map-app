# HudMapApp — Code Guide

> Every piece of code in the project, explained with pictures. If you only
> read one file, read this one.

---

## 1. The 30-second picture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HudMapApp (1 APK)                        │
│                                                                 │
│   Splash → Intro ×3 → Home ◄──► HUD / Settings                  │
│                        │                                        │
│          ┌─────────────┼─────────────┐                          │
│          ▼             ▼             ▼                          │
│  Navigation SDK's   Places SDK    Navigation SDK                │
│  bundled MapView                (guides driver,                 │
│  (draw map)         (find places) headless mode)                │
│          │             │             │                          │
│          └─────────────┼─────────────┘                          │
│                        ▼                                        │
│              Routes API (plan routes)                           │
│              plain HTTPS, not an SDK                            │
└─────────────────────────────────────────────────────────────────┘
```

**The golden rule of this codebase:**

```
Google owns DATA. The app owns STATE. Compose owns PIXELS.
```

- Google SDKs / APIs produce raw data (tiles, predictions, route JSON, NavInfo).
- `data/` + `navigation/` convert it into **immutable app-owned state**
  (`Destination`, `RoutePreview`, `NavigationState`).
- `ui/` only **reads** that state. It never calls Google directly
  (except `HomeMapView`, the one sanctioned map renderer).

⚠️ The Maps SDK cannot coexist with the Navigation SDK in one APK
(duplicate `com.google.android.gms.maps.*` classes — `play-services-maps`
is globally excluded in `app/build.gradle.kts`). All map rendering goes
through the Navigation SDK's bundled `MapView`; Maps Compose is unused.

---

## 2. How the app boots

```
MainActivity.onCreate()
   │  enableEdgeToEdge() + setContent { HudMapAppTheme { AppNavigation() } }
   ▼
HudMapApplication.onCreate()              ← runs BEFORE any screen
   │  NavigationApi.setApiKey(MAPS_API_KEY)   (skipped if key blank)
   │  navigationManager.initialize(this)     (navigator init, async)
   ▼
AppNavigation()                           ← typed NavHost
   Splash ──2s──► Intro1 ► Intro2 ► Intro3 ► Home
                                           ├──► HUD (in-place mirroring)
                                           └──► Settings (empty!)
```

| File | What it is |
|---|---|
| `MainActivity.kt` | Dumb host. Sets the Compose tree, nothing else. |
| `HudMapApplication.kt` | App startup. Sets the Google API key, creates the shared `NavigationManager`, and starts navigator init immediately (so the map can mount as soon as possible). |
| `ui/navigation/NavRoutes.kt` | 7 serializable destinations (`Splash`, `Introduction1..3`, `Home`, `HUD`, `Settings`). |
| `ui/navigation/AppNavigation.kt` | The `NavHost` + the shared, activity-scoped `NavigationSessionCoordinator`. |
| `AndroidManifest.xml` | Location permissions, `INTERNET`, `.HudMapApplication`, the Maps key placeholder, the launcher activity, and `navigation.TurnByTurnService` (`exported=false`). |

Navigator init details (`navigation/NavigationManager.kt`): if terms are not
accepted it parks in `TermsNotAccepted` (Home shows the SDK dialog, accepted
responses re-init); if `getNavigator` fails with
`LOCATION_PERMISSION_MISSING` the state resets so init retries as soon as
the permission is granted (retried from `HomeScreen`'s permission
LaunchedEffect). Transitions log under the `HudMapNav` tag.

---

## 3. The Home screen — where everything meets

```
┌──────────────────────────────── HomeScreen ────────────────────────────┐
│  HomeMapView (full-screen map) behind Compose overlays                 │
│    Top: greeting bar · search field + results                          │
│    Center: map + route polylines + destination marker                  │
│    Right: floating controls (satellite / my-location / dark style)     │
│    Bottom: route sheets, POI sheet, session banner                     │
│  State: selectedDestination · routePreviewState · selectedRoute ·      │
│         sessionState · navigationState · location (+ permission)       │
└────────────────────────────────────────────────────────────────────────┘
```

`HomeScreen()` takes injectable lambdas so previews/tests can swap the map.
Lifecycle wiring (two observers — don't merge them):

```
ON_START → permission+GPS check → locationProvider.startUpdates()
ON_STOP  → locationProvider.stopUpdates()

ON_STOP  → sessionCoordinator.onAppBackgrounded()   (30 s grace → pause)
ON_START → sessionCoordinator.onAppForegrounded()   (auto-resume if paused)
```

### Map display options (bundled MapView)

`HomeMapView` params `satelliteEnabled` / `darkStyleEnabled` (state lives in
`HomeScreen`, wired through the injectable `map` lambda) flip
`mapType = MAP_TYPE_SATELLITE` and apply `R.raw.map_style_dark.json`
(Google's official "Night mode" style). They are mutually exclusive — styling
never applies to satellite tiles. The GPS control button is tinted by fix
availability (`currentLocation != null`).

### Route selection and safety rails

- The selected route renders as ~24 overlapping polyline chunks colored
  along `MainGradient` (`gradientArgbAt` samples sky blue → purple → pink);
  alternatives are single gray polylines with distinct z-indexes so
  "topmost drawn" is well defined.
- Google's SDK fires the polyline click listener once per **overlapping**
  polyline, so `PolylineClickGate` collects callbacks within a 60 ms window,
  excludes the already-selected route, and picks the topmost candidate.
- POI clicks (`setOnPoiClickListener`) open `PoiDetailsSheet` — but go
  no-op while a destination is selected/confirmed or previews are loading,
  so tapping the map never wipes the selection.
- Route ids are index-based and unique (`route-0`, `route-1`, …); the
  Routes API's `routeLabels` is only `DEFAULT_ROUTE`/`ALTERNATIVE_ROUTE`.
- Dismissing `RoutePreviewSheet` only hides it; a bottom "Choose route" pill
  reopens it and the ✕ beside it clears the destination + routes explicitly.
- `MapPlaceholder` shows a spinner + status while the map cannot mount
  (navigator not Ready: terms/permission/init/error states).

---

## 4. Finding a place and route planning

```
User types ──300 ms debounce──► DestinationRepository.searchDestinations()
                                        │ Places Autocomplete (+0.5° bias box)
                                        ▼
                              List<Destination> (lat/lng placeholders)
                                        │ user taps one
                                        ▼
                              fetchPlaceDetails(placeId) → real lat/lng
Selected ──Confirm──► Confirmed ──requestRoutePreview──► RoutePreviewState
                                        │ map POI taps skip straight to
                                        ▼                       fetchPoiDetails
                              selectedRoute (exactly one, replaceable)
```

| File | Role |
|---|---|
| `data/model/Destination.kt` | Place DTO: `placeId, name, address, latitude, longitude, rating, userRatingsTotal, phoneNumber`. |
| `data/model/PoiDetails.kt` | `Destination` + downloaded photo `Bitmap`s for the POI sheet. |
| `data/model/SelectedDestinationState.kt` | `None → Selected → Confirmed`. Only `Confirmed` may start route planning. |
| `data/repository/DestinationRepository.kt` | Places wrapper: autocomplete (session token rotated after each search), `fetchPlaceDetails`, `fetchPoiDetails` (places call with `RATING`, `USER_RATING_COUNT`, `NATIONAL_PHONE_NUMBER`, `PHOTO_METADATAS`; up to 3 photos at 1080 px via `FetchPhotoRequest`; failures skip photos, never the place). |
| `ui/.../PoiDetailsSheet.kt` | Tapped-place sheet: name, rating, address, phone, photo row, Navigate button → selection + confirm + route preview. "Loading details…" while fields stream in. |
| `data/repository/RoutePlanningRepository.kt` | Routes API HTTPS (`computeRoutes`, `computeAlternativeRoutes:true`), field mask, package/cert headers, varint polyline decoding, `route-N` ids. |
| `navigation/RoutePreviewCoordinator.kt` | Preview state machine with stale-request sequence guard; auto-selects the first route. |
| `ui/.../RoutePreviewSheet.kt` | Route cards (`Fastest route`, `Alternative N`, `X km · Y min`), Start navigation. |

Stale-request protection (used twice): a monotonically increasing sequence
drops results whose session/request moved on.

---

## 5. The live navigation session

```
NavigationManager (app-scoped, Ready navigator)
        ▼
NavigationSessionCoordinator.startNavigation(dest, route)
        │ navigator.setDestinations(placeId waypoint, CustomRoutesOptions route token)
        │ navigator.startGuidance()
        │ navigator.registerServiceForNavUpdates(appId, TurnByTurnService, options)
        ▼
TurnByTurnService ◄── MSG_NAV_INFO bundle ─ TurnByTurnManager.readNavInfoFromBundle
        │ (in process)                              │
        ▼                                           ▼
TurnByTurnNavStore.latest ──2 s poll──► GuidanceSnapshot (current/next step,
        │                               distance to maneuver, totals, rerouting)
        ▼
GuidanceMapper → NavigationState (ManeuverType, instruction, progress, ETA)
        ▼
Home session banner + HUD screen
```

- `registerServiceForNavUpdates` **must** use the applicationId and requires
  `setNumNextStepsToPreview`, `setGeneratedStepImagesType`, and
  `setDisplayMetrics` on the options builder.
- If the TBT service is unavailable, `readGuidance()` falls back to
  `navigator.getTimeAndDistanceList()` totals (remaining distance/time only
  — maneuvers disappear but arrival/off-route/voice keep working).
- `NavigationState` powers everything the driver sees: `ManeuverType`
  (18 values incl. `ROUNDABOUT_EXIT`) → arrow rotation or roundabout glyph.
- Starting twice is a no-op; arrival and stop are terminal; `retryStart()`
  reuses the cached destination+route; interruption states offer
  Resume/Retry via `recoveryActionFor`/`userMessageFor` (raw SDK strings
  never reach the driver).
- Background policy: pause (30 s grace on `ON_STOP`), not a foreground
  service.

---

## 6. The HUD screen

```
HUDScreen (landscape forced, brightness maxed)
   ┌──────────┬─────────────────────────────┬───────────────┐
   │ distance │  big maneuver glyph + text  │ ETA │ time    │
   │ 300 m    │  (pulsing, gradient arrow)  │ 14:32  12 min │
   │ ▲ next   │             "Then …"        │               │
   └──────────┴─────────────────────────────┴───────────────┘
        ▲ small next-maneuver preview + "next" label
```

- Values are read from the `navigationState` flow; the SDK pushes a new
  `NavInfo` on every completed step, so the glyph/instruction/next preview
  advance automatically.
- `ManeuverGlyph` picks between the rotating gradient arrow and the drawn
  roundabout glyph (`RoundaboutExitGlyph`): a ring + entry stem + exit arm
  at `30° + (exit-1)·60°` counterclockwise (exit 1 upper right, straight
  through = up). Exit number comes from the SDK's roundabout turn number.
- The "Mirror" pill flips the guidance layer in place (`scaleX = -1`) — the
  double-flip through windshield glass reads correctly. Close/mirror
  controls stay outside the flipped layer. There is no mirrored route; the
  old `MirroredHUDScreen` was removed.
- `HudSessionContent` maps every session state (Idle/Starting/Guidance/
  Interrupted/Arrived/Error) to a defined variant; rerouting/off-route
  freeze the last live values so nothing tears mid-transition.

---

## 7. Location & permissions

```
OS permission + GPS switch ──► FusedLocationProvider (Google boundary)
                                             │ app-owned provider
                                             ▼
                     SharedFlow<AppLocation> + StateFlow<LocationState>
```

- `FusedLocationProvider.kt` — 5 s interval, high accuracy, owns Google
  callback lifecycle.
- `LocationPermissionHandler.kt` — runtime permission + rationale +
  Settings fallback; drives the banners and overlays.
- Location permission is also what lets `NavigationManager` reach `Ready`:
  `getNavigator` fails with `LOCATION_PERMISSION_MISSING` before the grant,
  and the coordinator retries on grant. This is why the emulator/CI test
  flow should pre-grant `ACCESS_FINE_LOCATION` or accept the runtime dialog.

---

## 8. Theme & build

- `ui/theme/`: `Color.kt` (brand palette; `MainGradient` = sky blue →
  purple → pink, used by HUD glyphs and the selected route polyline),
  `Theme.kt`, `Type.kt` (Poppins/Nunito), `components/` (HudButton/Card/
  Chip/TopBar/…).
- `app/build.gradle.kts`: reads `MAPS_API_KEY` from `local.properties` →
  manifest placeholder + `BuildConfig`; **excludes `play-services-maps`**
  (Navigation SDK bundles its own copy); compile/target SDK 37, minSdk 26,
  desugaring, Java 11, `en`-only locales.
- `gradle/libs.versions.toml`: AGP 9.3.2, Kotlin 2.2.10, Compose BOM
  2026.02.01, Navigation 7.6.1, Places 5.3.0. (`play-services-maps` remains
  declared in the catalog but unused.)
- `gradle.properties`: `android.uniquePackageNames=false`.
- `res/raw/map_style_dark.json`: dark vector style for the map's night mode.
- Tests: `app/src/test` (unit) — route parsing, preview coordinator,
  guidance/session mapping, HUD presentation; `app/src/androidTest` for
  sheets/banners instrumentation (local emulator only).
- Logcat tags: `HudMapNav` (navigator init + TBT registration), `HudMapMapView`
  (map polyline/POI lifecycle), `RoutePreview` (route planning).

---

## 9. Extending the app

- The `navigation/` pipeline is the single seam for Google data: swap
  `SdkNavigatorAdapter` for another adapter (it exists as a test fake
  pattern already) without touching the UI.
- UI screens only consume state flows; adding screens means adding a route
  to `AppRoute`, a composable in `AppNavigation`, and reading existing
  flows.
- The Services/Places/Routes knowledge needed for QA is summarized in
  `PROJECT_DOCUMENTATION.md` (same repo, under
  `app/src/main/java/com/example/hudmapapp/`).
