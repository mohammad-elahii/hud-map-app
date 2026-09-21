# HudMapApp — Code Guide (read this before Phase 5)

> Every piece of code in the project, explained with pictures. If you only
> read one file, read this one.

---

## 1. The 30-second picture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HudMapApp (1 APK)                        │
│                                                                 │
│   Splash → Intro ×3 → Home ◄──► HUD / MirroredHUD / Settings   │
│                        │                                        │
│          ┌─────────────┼─────────────┐                          │
│          ▼             ▼             ▼                          │
│     Maps SDK      Places SDK    Navigation SDK                  │
│     (draw map)    (find places) (guide driver)                  │
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

- Google SDKs / APIs produce raw data (tiles, predictions, route JSON, callbacks).
- `data/` + `navigation/` convert it into **immutable app-owned state**
  (`Destination`, `RoutePreview`, `NavigationState`).
- `ui/` only **reads** that state. It never calls Google directly
  (except `HomeMapView`, which is the one sanctioned map renderer).

---

## 2. How the app boots

```
MainActivity.onCreate()
   │  enableEdgeToEdge() + setContent { HudMapAppTheme { AppNavigation() } }
   ▼
HudMapApplication.onCreate()              ← runs BEFORE any screen
   │  NavigationApi.setApiKey(MAPS_API_KEY)   (skipped if key blank)
   │  navigationManager = NavigationManager() (shared singleton holder)
   ▼
AppNavigation()                           ← typed NavHost
   Splash ──2s──► Intro1 ► Intro2 ► Intro3 ► Home
                                                   ├──► HUD
                                                   ├──► MirroredHUD
                                                   └──► Settings (empty!)
```

| File | What it is |
|---|---|
| `MainActivity.kt` | Dumb host. 23 lines. Sets the Compose tree, nothing else. |
| `HudMapApplication.kt` | App startup. Sets the Google API key once, creates the shared `NavigationManager`. |
| `ui/navigation/NavRoutes.kt` | 8 serializable destinations (`Splash`, `Introduction1..3`, `Home`, `HUD`, `MirroredHUD`, `Settings`). |
| `ui/navigation/AppNavigation.kt` | The `NavHost`. No arguments passed between screens (yet — a Phase 5 problem). |
| `SplashScreen.kt` | Waits 2 s, navigates to Intro1. |
| `IntroductionScreen1/2/3.kt` | Onboarding pages (static). |
| `SettingScreen.kt` | **Empty.** Phase 8 work. |

`AndroidManifest.xml` declares location permissions, `INTERNET`,
`android:name=".HudMapApplication"`, the Maps key
`com.google.android.geo.API_KEY = ${MAPS_API_KEY}` (injected from
`local.properties` at build time — never committed), and `MainActivity`
as the launcher.

---

## 3. The Home screen — where everything meets

Home is the only "real" screen. Everything else is prototype.

```
┌──────────────────────────────── HomeScreen ────────────────────────────────┐
│                                                                            │
│   ┌──────────────┐  ┌──────────────────┐  ┌─────────────────────────────┐   │
│   │ HomeMapView  │  │ Search + sheets  │  │ Banners (stacked bottom)    │   │
│   │ (the map)    │  │                  │  │                             │   │
│   │              │  │ DestinationSearch│  │ LocationStatusBanner        │   │
│   │ • camera     │  │ DestinationBottom│  │ NavigationSessionBanner    │   │
│   │ • marker     │  │ RoutePreviewSheet│  │ LocationBlockOverlay (full) │   │
│   │ • polylines  │  │                  │  │                             │   │
│   └──────────────┘  └──────────────────┘  └─────────────────────────────┘   │
│                                                                            │
│   State collected: selectedDestination · routePreviewState · selectedRoute  │
│                    sessionState · navigationState · location (+ permission) │
└────────────────────────────────────────────────────────────────────────────┘
```

`HomeScreen()` takes injectable lambdas so previews/tests can swap the map:

```
HomeScreen(
  navController,
  viewModel = HomeViewModel.Factory(appContext, MAPS_API_KEY),     // selection + previews
  sessionCoordinator = NavigationSessionCoordinator.Factory(navManager), // guidance session
  map = { loc, trigger, selected, routes, id, onSelect -> HomeMapView(...) },
  onStartNavigation = {}                                            // hook for Phase 5
)
```

Lifecycle wiring (two observers — don't merge them):

```
ON_START → permission+GPS check → locationProvider.startUpdates()
ON_STOP  → locationProvider.stopUpdates()

ON_STOP  → sessionCoordinator.onAppBackgrounded()   (30 s grace → pause)
ON_START → sessionCoordinator.onAppForegrounded()   (auto-resume if paused)
```

> ⚠️ Known wart: `navigationManager.initialize()` runs in `HomeScreen`'s
> `LaunchedEffect`, so the SDK only initializes when Home is first composed —
> not at app start. Fine for now, fragile if navigation ever starts elsewhere.

---

## 4. Finding a place (Places SDK)

```
User types ──300 ms debounce──► DestinationRepository.searchDestinations()
                                        │ Places Autocomplete (+0.5° bias box)
                                        ▼
                              List<Destination>  (lat/lng = 0,0 placeholders!)
                                        │ user taps one
                                        ▼
                              fetchPlaceDetails(placeId) ──► real lat/lng
                                        │
                                        ▼
                         Selected ──Confirm──► Confirmed
```

| File | Role |
|---|---|
| `data/model/Destination.kt` | The place DTO: `placeId, name, address, latitude, longitude` (+ unused `rating` fields — repo never fills them). |
| `data/model/DestinationSearchState.kt` | Search UI machine: `Idle → Searching → Results | Empty | Error`. (Note: its `Selected` variant duplicates `SelectedDestinationState` — two sources of "selected", a cleanup candidate.) |
| `data/model/SelectedDestinationState.kt` | Post-search lifecycle: `None → Selected → Confirmed`. Only `Confirmed` may start route planning. |
| `data/repository/DestinationRepository.kt` | Places wrapper. `create()` guards `Places.initialize()`; rotates the `AutocompleteSessionToken` after each search (billing correctness); blank query → empty list, never an error. |
| `ui/.../DestinationSearch.kt` | Pill search bar (`DestinationSearchBar`) + animated dropdown (`DestinationSearchResults`, max 300 dp, spinner / error colors inside). |
| `ui/.../DestinationBottomSheet.kt` | "Selected Destination" sheet with Confirm / Clear. Hides itself before firing the callback (`hide-then-act`). |

---

## 5. Planning routes (Routes API — plain HTTPS, not an SDK)

```
Confirmed destination + current origin
   │  validate: non-null, finite, ≠ (0,0), lat ±90, lng ±180
   ▼
RoutePlanningRepository.requestRoutes()
   │  RoutesApiDataSource: POST https://routes.googleapis.com/directions/v2:computeRoutes
   │  DRIVE + TRAFFIC_AWARE + computeAlternativeRoutes:true, X-Goog-FieldMask picks fields
   │  X-Android-Package / X-Android-Cert identify the app to Google
   ▼
RoutePreviewState: Idle → Loading → Available(routes) | Empty | Cancelled | Error(type)
                                              │ auto-selects routes[0]
                                              ▼
                                    selectedRoute (exactly one, replaceable)
```

Stale-request protection (the sequence trick, used twice in this codebase):

```
request #1 ──slow─────────────► result arrives, sequence moved on → DROPPED ✓
request #2 ──fast──► result arrives, sequence matches → APPLIED ✓
```

| File | Role |
|---|---|
| `data/model/RoutePreview.kt` | `RouteCoordinate`, `RoutePreview(id, polylinePoints, encodedPolyline, distanceMeters, durationSeconds, routeToken?, label?)`, `RoutePreviewError` (8 stable categories: Network/Timeout/Authentication/QuotaExceeded/InvalidRequest/NoResults/Cancelled/Unknown), `RoutePreviewState` (Idle/Loading/Available/Empty/Cancelled/Error). `id` = `routeLabels[0]` or `route-N`; `label` currently holds the raw `staticDuration` string. |
| `data/repository/RoutePlanningRepository.kt` | Validation + `RoutesApiDataSource` (HTTP, status→error map: 429→Quota, 401/403→Auth, 400→Invalid, timeouts→Timeout, DNS/IO→Network). Parses JSON with `ignoreUnknownKeys`, drops blank polylines, decodes Google varint polylines (÷1E5). `signingCertificateSha1()` reads the APK cert for the `X-Android-Cert` header. |
| `data/repository/RouteLogger.kt` | Pluggable logger (`android()` → logcat tag `RoutePreview`, `noop()` → tests). Filter logcat with `adb logcat -s RoutePreview:D`. |
| `navigation/RoutePreviewCoordinator.kt` | `ViewModel` owning the preview machine: `AtomicLong` sequence, cancels the prior job, maps `Success/Empty/Failure`, auto-selects first route. `selectRoute(id)` only works in `Available`; `clearRoutePreview()` resets to `Idle`. |
| `ui/.../HomeViewModel.kt` | Selection state (`None/Selected/Confirmed`) + delegates to the preview coordinator. `requestRoutePreview()` silently returns unless `Confirmed` (watch for this in logs). `Factory(context, apiKey)` builds repo+coordinator. |
| `ui/.../RoutePreviewSheet.kt` | "Choose a route" sheet: spinner → cards (`Fastest route`, `Alternative N`, `X km · Y min`, selected = purple border) → Start navigation (defaults to first route). Errors map to friendly strings — raw API text never reaches the driver. |
| `ui/.../HomeMapView.kt` | Draws the polylines: selected purple `0xFF7C4DFF` 14 dp z=2 on top, alternatives gray 9 dp z=1, round joints, clickable → `onRouteSelected`. Skips routes with <2 points. Auto-zooms to the selected route bounds (guarded try/catch). Destination marker + recenter + error overlay live here too. |
| `ui/.../MapPlaceholder.kt` | Static grid fallback when the map can't render. |

---

## 6. The live navigation session (Navigation SDK)

This is the heart of the app. Three objects, three jobs:

```
┌──────────────────┐   owns init    ┌───────────────────────┐  drives  ┌────────────────┐
│ NavigationManager │───────────────►│ NavigationSessionCoord │─────────►│ NavigatorAdapter│
│  Uninitialized →  │  Ready(nav)    │  Idle→…→Active→…       │  wraps   │  (real SDK)     │
│  Initializing →   │                │  + NavigationState     │  SDK     │  SdkNavigator   │
│  Ready | Error    │                │  (maneuver/progress)   │          │  Adapter        │
└──────────────────┘                └───────────────────────┘          └────────────────┘
```

Session lifecycle (every transition is a tested state):

```
                              ┌─ RouteFailed / GuidanceFailed ─► Error ──retryStart──┐
                              │      (NETWORK_ERROR → NetworkError)                   │
Idle ──start──► Starting ──OK + guidance running──► Active ──stop──► Stopping ──► Stopped
                                              │  ▲                                    │
                         off-route            │  │ route changed                       │ arrival
                              ▼               │  │ (clears rerouting)                  ▼
                           OffRoute ──resolve──┴──┴──► Rerouting ──► Active        Arrived (frozen:
                              │                                              no more updates)
                              │ location/GPS/network/background lost
                              ▼
                         Interrupted ──resume──► Active (same SDK session, no restart)
```

Background policy (a deliberate choice, documented): **pause, not a foreground
service** — no notification permission, no service lifecycle. `ON_STOP` starts a
30 s grace timer; quick return cancels it silently, overstaying pauses to
`Interrupted(GUIDANCE_PAUSED)`, foreground auto-resumes. Revisit if turn-by-turn
audio ever needs to survive the background.

Arrival and stop are **terminal**: listeners are removed, the adapter is
dropped, later callbacks are ignored. Starting twice is a no-op. `retryStart()`
reuses the cached destination+route. Recovery mapping (`recoveryActionFor` →
RETRY_START / RESUME / STOP / NONE) and driver strings (`userMessageFor` —
never leaks raw SDK text) live next to the states.

| File | Role |
|---|---|
| `navigation/NavigationManager.kt` | One-shot SDK init. `initialize()` no-ops unless `Uninitialized`; `shutdown()` = `stopGuidance + cleanup`. |
| `navigation/NavigationSessionState.kt` | Session states (`Idle/Starting/Active/Rerouting/OffRoute/Interrupted/Stopping/Stopped/Arrived/Error`), `InterruptionReason`, `NavigationSessionError`, plus pure `recoveryActionFor` / `userMessageFor` mappers. |
| `navigation/NavigationSessionCoordinator.kt` | The orchestrator `ViewModel` (+ `Factory(navigationManager)` so it survives rotation as an activity-scoped VM). Owns `sessionState` + `navigationState`, the sequence guard, 4 SDK listeners, the 2 s guidance poll, the 30 s background timer, retry/resume, and `startSimulator()` for emulator runs. |
| `navigation/NavigatorAdapter.kt` | Test seam: `NavigatorAdapter` interface + `SdkNavigatorAdapter`. Builds the `Waypoint` (placeId preferred, latLng fallback), uses `CustomRoutesOptions(routeToken)` when present. Feed = main-thread `Handler` every 2 s (`FEED_INTERVAL_MILLIS`, 1 s initial delay). `addRemainingTimeOrDistanceChangedListener` hardcodes 60 s / 100 m thresholds. |
| `navigation/NavigationState.kt` | HUD-ready models: `ManeuverType` (17 values), `ManeuverInfo`, `NavigationProgress` (all-nullable), `GuidanceStatus`, `NavigationState`, `NavigationDataError`. |
| `navigation/GuidanceSnapshot.kt` | Raw-data DTO decoupling mappers from UI state. |
| `navigation/GuidanceMapper.kt` | `Snapshot → NavigationState`: maneuver code→type (grouped names), blank instruction→`"Continue"`, ETA = now + remaining, preserves last maneuver when new data is null, null snapshot → `GuidanceUnavailable`. |
| `navigation/TurnByTurnMapper.kt` | Reflection adapter over opaque `NavInfo` (no compile-time `turnbyturn` dep — renames degrade to null, never crash). ⚠️ Currently half-wired: `readTurnByTurn()` reads a `turnService` field that is **never assigned**, so the live path always falls back to `getTimeAndDistanceList()` (distance/time only, no maneuvers on-device yet — that's #54's remaining work). |
| `navigation/FakeGuidance.kt` | `fakeStep / fakeGuidance / fakeNavigationState` factories for tests and previews. |
| `ui/.../NavigationSessionBanner.kt` | Persistent banner: Starting spinner; Active shows `instruction · distance · remaining · ETA` (falls back to route summary); Rerouting/OffRoute/Interrupted variants + Resume; Error + conditional Retry; always a Stop (✕) button. Pure display — all decisions come from `recoveryActionFor` / `userMessageFor`. |

---

## 7. Location & permissions

```
OS permission + GPS switch ──► FusedLocationUpdateSource (Google boundary)
                                            │ one registration token
                                            ▼
                                 SharedLocationProvider (app-owned)
                                   ├─ SharedFlow<AppLocation> (replay 1)
                                   ├─ StateFlow<LocationState>
                                   └─ StateFlow<Boolean> tracking
                                            │ passive observation
                                            ▼
                              Home now; navigation/HUD consumers later

LocationPermissionHandler drives PermissionRequired / ServicesDisabled UI;
LocationStatusBanner and LocationBlockOverlay render the typed state.
```

| File | Role |
|---|---|
| `location/AppLocation.kt` | Platform-agnostic fix. Bearing/course and speed are nullable; `null` means the platform did not report the measurement, while zero remains valid north/stationary data. |
| `location/LocationProvider.kt` | Hot/passive shared updates + typed state; only explicit `startUpdates()` / `stopUpdates()` control acquisition. |
| `location/LocationUpdateSource.kt` | Android-free source/registration boundary used by deterministic fakes. |
| `location/SharedLocationProvider.kt` | Application-owned single-registration coordinator. Replays the latest timestamped fix and rejects stale callbacks/generations. |
| `location/FusedLocationProvider.kt` | Thin `FusedLocationUpdateSource` adapter (5 s updates, 2 s fastest) that owns Google callback creation/removal and honors `hasBearing()` / `hasSpeed()`. |
| `domain/driving/DrivingContextState.kt` | Immutable Phase 6 vocabulary for normalized heading/speed, orientation, movement, source, reliability, freshness, and timestamps. Producers/fusion remain later issues. |
| `location/LocationPermissionState.kt` | `LocationState`, `LocationBlockReason`, and OS checks (`hasLocationPermission`, `isLocationEnabled`, `isNetworkAvailable`). |
| `location/LocationPermissionHandler.kt` | Compose permission orchestrator with rationale vs "go to Settings" branches. |
| `location/LocationPermissionOverlay.kt` | Banner (soft problems) + overlay (hard blocks). |

---

## 8. HUD screens (today: pretty mockups, tomorrow: live)

```
HUDScreen ──Mirror button──► MirroredHUDScreen (same layer, scaleX = -1)
     │                                │
     └──── HUDNavigationLayer ─────────┘
              pulsing arrow + "300 m" + "Turn left" + car glyph   ← ALL HARDCODED
```

`HUDScreen.kt` / `MirroredHUDScreen.kt` consume **zero** navigation state —
no `collectAsState`, no arguments. Mirroring reuses the layer instead of
duplicating it (the close button stays unmirrored on purpose). **Phase 5 =
feed `navigationState` in here.**

---

## 9. Theme & build

- `ui/theme/`: `Color.kt` (brand palette incl. `DeepPurple30`), `Theme.kt`
  (`HudMapAppTheme`, light+dark), `Type.kt` (Poppins/Nunito fonts in
  `res/font/`), `components/` — `HudButton/Card/Chip/TopBar/BottomBar/Divider/Error/Loading/IconButton/Section`.
- `app/build.gradle.kts`: reads `MAPS_API_KEY` from `local.properties` →
  manifest placeholder + `BuildConfig`; **excludes `play-services-maps`**
  (Navigation SDK bundles its own copy — duplicate-class crash otherwise);
  `compileSdk/targetSdk 37, minSdk 26`, `en`-only locales, desugaring + Java 11.
- `gradle/libs.versions.toml`: AGP 9.3.2, Kotlin 2.2.10, Compose BOM 2026.02.01,
  Navigation 7.6.1, Maps Compose 6.12.0, Places 5.3.0.
- `gradle.properties`: `android.uniquePackageNames=false` (works around a
  Chromium cronet namespace clash — issue 406926302).
- Tests: `app/src/test` (51 unit tests, run in CI via `./gradlew test`) +
  `app/src/androidTest` (Compose sheet/banner tests, local emulator only).
  `Phase3TestNotes.md` documents coverage, SDK assumptions, the simulator path,
  and the live-device checklist.

---

## 10. Where Phase 5 plugs in

```
NavigationSessionCoordinator.navigationState   ← ALREADY FLOWING, tested
        │
        ▼ (new)
HUDScreen(navController, navigationState)      ← pass it in via AppNavigation
        │
        ▼ (new)
HUDNavigationLayer(state)                      ← replace hardcoded strings
        │
        ├── maneuver arrow rotation ← ManeuverType
        ├── "300 m"               ← distanceToManeuverMeters
        ├── "Turn left"           ← instruction
        ├── "Then …"              ← nextManeuver
        └── remaining / ETA       ← progress
```

You don't need new data plumbing — just new pixels reading the state that
#54 already produces. Good luck. 🗺️
