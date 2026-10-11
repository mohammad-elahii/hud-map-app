# HudMapApp

A windshield-style HUD navigation app for Android. Real-time turn-by-turn guidance rendered in a custom landscape head-up display, projected onto the car's windshield.

> Google owns the data. The app owns the state. Compose owns the pixels.

|  |  |
|---|---|
| minSdk / targetSdk | 26 / 37 |
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (Material 3) |
| Navigation SDK | 7.6.1 (headless) |
| CI | `Android CI` on every push and PR to `develop` |

## Screenshots

The images below are best viewed at full page width.

### Home map view

Full-screen dark map, continuous location tracking, POI interactions.

![Home map view](app/readme/intro%201.png)

![Route preview and search flow](app/readme/intro%202.png)

### Search and route planning

Places SDK autocomplete with debounced suggestions. Route preview with gradient polylines, fastest and alternative route cards, ETA and distance totals.

![Search and route preview](app/readme/intro%203.png)

### HUD mode

Landscape-only, max-brightness head-up view. Big maneuver arrow, distance, ETA and remaining time. A mirror toggle flips the display horizontally for windshield projection.

![HUD mode](app/readme/intro%204.png)

### Live navigation session

Turn-by-turn guidance feed driven by the Navigation SDK: next maneuver, progress and ETA.

![Live navigation session](app/readme/intro%205.png)

## Features

### Map and location
- Full-screen dark map rendered through the Navigation SDK's bundled `MapView` (Maps SDK and Navigation SDK cannot coexist in one APK, so all rendering goes through the Navigation SDK).
- Continuous fused location updates collected as a `SharedFlow`, camera follows the device with proper lifecycle handling.
- Custom dark map style via `map_style_dark.json`.

### Search and POI
- Places SDK autocomplete with 300ms debounce and session tokens.
- POI details sheet: name, rating, address, phone, and up to 3 photos at 1080px.

### Route planning
- Routes API over plain HTTPS (`computeRoutes`) with alternative routes and varint polyline decoding.
- Gradient route polylines rendered in ~24 chunks with a 60ms polyline-click de-dupe gate.
- Route preview state machine with a stale-request sequence guard.

### Guidance
- One-shot navigator lifecycle: `Uninitialized -> Initializing -> Ready -> Error`, retried when the permission is granted.
- Session coordinator: `Idle -> Starting -> Active -> ... -> Arrived/Error`, SDK listeners, 2s guidance feed poll, 30s background-grace pause policy.
- Headless turn-by-turn updates: manifest-declared `TurnByTurnService` (a Messenger service) decodes `MSG_NAV_INFO` bundles into a `TurnByTurnNavStore`, mapped to `GuidanceSnapshot` and `NavigationState` across 18 maneuver types (roundabouts included).
- All state is immutable and app-owned; the UI only reads it.

### HUD
- `SENSOR_LANDSCAPE`-locked, max-brightness screen.
- Procedurally drawn rotating gradient maneuver arrow and roundabout ring glyphs.
- Mirror toggle (`scaleX = -1`) for double-facing windshield projection.

### Reusable UI components
`HudButton`, `HudCard`, `HudChip`, `HudTopBar`, `HudBottomBar`, `HudDivider`, `HudError`, `HudIconButton`, `HudLoading`, `HudSection`.

## Architecture

Single-module (`:app`), single-activity Jetpack Compose app. MVVM + repository pattern with manual dependency injection - no DI framework.

```
Google SDKs / APIs
      Navigation SDK • Places SDK • Routes API • Fused Location
                    |
                    v
data/          Google responses -> immutable app state
               Destination, PoiDetails, RoutePreview
               DestinationRepository, RoutePlanningRepository
                    |
                    v
navigation/    Live session state machines
               NavigationManager, NavigationSessionCoordinator,
               NavigatorAdapter, TurnByTurnService, TurnByTurnNavStore
                    |
                    v
ui/            Compose renders state only
               homeScreen, hudScreen, splashScreen, settingScreen
               theme/components (Hud* library), navigation (NavHost)
```

Flow: Splash -> 3 introduction pages -> Home (map, search, POI, route preview) -> HUD.

`MainActivity` is a dumb host (`setContent { HudMapAppTheme { AppNavigation() } }`). `HudMapApplication` wires the API key and the shared `NavigationManager`.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2.10 |
| Gradle / AGP | Gradle 9.5.0 / AGP 9.3.2 |
| UI | Jetpack Compose, BOM 2026.02.01, Material 3 |
| Navigation | Navigation Compose 2.10.1, typed serializable routes |
| Location / Maps | Navigation SDK 7.6.1, Places SDK 5.3.0, play-services-location 21.3.0 |
| Async | Coroutines 1.7.3 |
| Networking | `HttpURLConnection` + Routes API |
| Serialization | kotlinx-serialization-json 1.11.0 |
| Desugaring | desugar_jdk_libs_nio 2.1.5 (Java 11 source/target, Java 17 toolchain) |
| Testing | JUnit 4.13.2, Espresso, compose ui-test, kotlinx-coroutines-test |

Note: Navigation SDK and Maps SDK cannot coexist in a single APK. The `play-services-maps` dependency is declared in the version catalog but globally excluded from the build - all map drawing goes through the Navigation SDK's bundled `MapView`.

## Version control workflow

The repo follows a two-stable-branch model, and every change reaches those branches the same way.

**Branches**

- `develop` - the integration trunk. All work lands here via pull requests, and CI runs on every push and PR touching it.
- `main` - the protected release branch. Only updated by merging `develop`. A repository ruleset enforces: required `Android CI` status checks, extra approval for changes not attributed to the PR author, and no deletion or non-fast-forward updates.

**Branching per ticket**

Every work item gets its own short-lived branch off `develop`, named after the ticket it implements:

```
feature/project-architecture     feature/google-maps-integration
feature/app-theme                feature/route-preview
feature/destination-search       feature/rendering-guidance
feature/navigation-architecture  feature/device-orientation
feature/splash-screen            feature/location-tracking
feature/hud-display-screen       feature/navigation-error-handling
feature/live-feed                chore/remove-kotlin-comments
```

Commits reference their issue or PR, so the whole flow is traceable: `#64 tracking location device`, `#61 HUD states and tests`, `#44 route preview tests`.

**Conflict hygiene**

When long-lived branches drift (for example, a theme revert on `main` against active work on `develop`), conflicts are resolved explicitly and merged back into `develop` instead of being hidden inside a release merge. During the PR #72 preparation merge:

- stale template files were resolved in favor of `develop`,
- font resources deleted on `main` by an old revert were restored,
- `Theme.kt` was re-pinned to the real theme tokens after a bad auto-merge.

A follow-up `Restore develop version of Theme.kt` commit plus a green CI run verified the resolution. Every merge to `develop` is a meaningful, verified build, and only verified code reaches `main`.

## CI/CD

The pipeline is `.github/workflows/android-ci.yml`, job `build-and-test`, triggered on every push to `develop` and every pull request into `develop`:

```yaml
name: Android CI
on:
  push:
    branches: [ develop ]
  pull_request:
    branches: [ develop ]
```

Steps: `actions/checkout@v4`, JDK 17 (Temurin) via `actions/setup-java@v4`, Gradle setup and caching via `gradle/actions/setup-gradle@v4`, then `./gradlew assembleDebug` and `./gradlew test`.

The pipeline did its job in practice:

- It caught a broken merge automatically - when `Theme.kt` got corrupted during manual conflict resolution (dangling references to template colors), the build failed immediately and pointed at the exact file.
- It verified the fix - after restoring `Theme.kt`, the next run went green in 4m43s, producing the status check the `main` ruleset requires.
- It gates merges - the required status check means no red build can ever update `main`.

## Testing

Run the unit tests with:

```
./gradlew testDebugUnitTest
```

| Area | Tests |
|---|---|
| Navigator init | `NavigationInitTest.kt` |
| Session state machine | `NavigationSessionTest.kt` |
| Guidance and state mapping | `NavigationStateTest.kt`, `HudGuidancePresentationTest.kt`, `HudSessionContentTest.kt` |
| Route preview and routes API | `RoutePreviewTest.kt` |
| Home screen | `HomeViewModelTest.kt` |
| Location | `AppLocationTest.kt`, `SharedLocationProviderTest.kt` |
| Driving context | `DrivingContextStateTest.kt` |

Instrumented (Compose UI) tests exist for `HudScreen`, `RoutePreviewSheet` and `NavigationSessionBanner` and currently run on a local emulator only.

## Getting started

Requirements: Android Studio with AGP 9 support, JDK 17, a Google API key with the Navigation SDK and Places enabled.

```
git clone https://github.com/mohammad-elahii/hud-map-app.git
cd hud-map-app
```

Add your key to `local.properties` (injected into the manifest and `BuildConfig` automatically):

```
MAPS_API_KEY=your_key_here
```

Build and test:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The Navigation SDK's terms of service must be accepted on the device or emulator on first run; the navigator will not initialize until then and the app surfaces the state through its lifecycle.

## Project structure

```
app/src/main/java/com/example/hudmapapp/
├── data/                 repositories + immutable models
├── domain/driving/       driving-context state
├── location/             fused provider, permission overlays
├── navigation/           NavigationManager, NavigationSessionCoordinator,
│                         NavigatorAdapter, TurnByTurnService, TurnByTurnNavStore,
│                         GuidanceMapper, RoutePreviewCoordinator
├── ui/
│   ├── navigation/       NavHost, typed serializable routes
│   ├── theme/            Color, Type, Theme, components/Hud*
│   └── screens/
│       ├── splashScreen/
│       ├── introductionScreens/
│       ├── homeScreen/   HomeMapView, DestinationSearch, PoiDetailsSheet,
│       │                 RoutePreviewSheet, HomeViewModel
│       ├── hudScreen/    HUDScreen, HudGuidancePresentation, HudManeuverIcons
│       └── settingScreen/
└── MainActivity.kt / HudMapApplication.kt
```

## Contributing

1. Create a branch off `develop`: `feature/your-ticket-slug`
2. Reference the ticket in your commit title: `#NN your short description`
3. Open a PR into `develop` and wait for `Android CI` to pass
4. Never update `main` directly
