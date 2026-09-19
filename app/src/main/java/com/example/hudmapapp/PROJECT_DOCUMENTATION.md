# HudMapApp Project Documentation and Product Roadmap

**Last reviewed:** 2026-09-19  
**Project:** Hud map app  
**Application ID:** `com.example.hudmapapp`  
**Status:** Active Android prototype; current Home scope is map display and location awareness

## 1. Product direction

HudMapApp is an Android application that is progressing toward a windshield-style navigation HUD. The product should not be treated as a replacement Google Maps UI. Google services provide the map, place data, route planning, and navigation machinery; HudMapApp owns the experience, state transformation, and custom HUD presentation.

The intended product split is:

```text
Google Maps SDK / Maps Compose
    → Render the interactive map

Google Places SDK
    → Search and resolve destinations

Routes API or Navigation SDK route planning
    → Calculate and expose candidate routes

Navigation SDK for Android
    → Run the active navigation session and provide live guidance events

HudMapApp navigation state
    → Transform guidance into product-specific state

Custom Compose HUD
    → Display maneuver, distance, progress, and status in the HudMap visual language
```

The current implementation is intentionally earlier than this target. Home currently shows the Google map, location controls, destination search UI, and destination selection flow. Route calculation, route polylines, route selection, active navigation, and live HUD data are not currently implemented.

## 2. Current technology and build profile

| Area | Current implementation |
|---|---|
| Module structure | One Android application module: `:app` |
| Language | Kotlin |
| UI | Jetpack Compose and Material 3 |
| Navigation | Navigation Compose with serializable typed routes |
| Minimum Android version | API 26 |
| Compile/target SDK | API 37 |
| Java compatibility | Java 11 |
| Map renderer | Google Maps SDK for Android through Maps Compose |
| Place search | Google Places SDK for Android |
| Device location | Google Play Services Fused Location Provider |
| Route calculation | Not currently present; planned for Phase 3 |
| Active navigation | Not currently present; planned for Phase 3/4 |
| HUD | Compose prototype with static content |
| Sensors | Not implemented |
| Persistence | Not implemented |
| Dependency injection | Manual construction; no DI framework |

Important configuration files:

- `settings.gradle.kts` — repository configuration and module inclusion.
- `build.gradle.kts` — root plugin declarations.
- `app/build.gradle.kts` — Android configuration, Maps key injection, and dependencies.
- `gradle/libs.versions.toml` — centralized dependency versions.
- `gradle.properties` — Gradle behavior.
- `.github/workflows/android-ci.yml` — CI workflow configuration.

## 3. Local setup

### Prerequisites

Use an Android development environment with:

- Android SDK platform/API 37 available.
- A compatible recent Android Gradle Plugin, Kotlin, Gradle, and Java 11 toolchain.
- Google Maps and Places access configured in Google Cloud.
- A device or emulator running Android API 26 or newer.
- A restricted Android API key configured locally.

Navigation SDK work will add its own dependency and Google Cloud setup requirements. Those requirements must be confirmed against the SDK version selected for implementation before Phase 3 begins.

### API-key configuration

Create or update the ignored root file `local.properties`:

```properties
MAPS_API_KEY=your_restricted_google_api_key
```

`app/build.gradle.kts` reads this value and injects it into `BuildConfig.MAPS_API_KEY` and the manifest placeholder used by the Maps SDK. The current app uses the same configuration value for Google service initialization.

An Android API key is not a server-side secret: it can be extracted from a built APK. Restrict it in Google Cloud by application ID and signing-certificate fingerprints, enable only the required APIs/SDKs, configure quotas, and rotate it if exposed. Do not commit `local.properties` or place a real key in documentation, screenshots, or logs.

### Common Gradle commands

From the repository root:

```text
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

On Windows:

```text
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` requires a connected emulator or physical device. The app may build with a missing key, but Google Maps and Places functionality will fail at runtime.

## 4. Repository layout

```text
Hudmapapp/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/hudmapapp/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ARCHITECTURE.md
│       │   │   ├── data/
│       │   │   ├── location/
│       │   │   └── ui/
│       │   ├── res/
│       │   └── keepRules/
│       ├── test/
│       └── androidTest/
├── gradle/
│   └── libs.versions.toml
├── .github/workflows/android-ci.yml
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

The active source tree currently contains `data`, `location`, and `ui` packages. A populated `domain` package and sensor implementation are not present yet. The older route-specific additions were intentionally removed so the project can return to a controlled map-only baseline before navigation is reintroduced.

## 5. Current architecture

### Runtime dependency flow

```text
MainActivity
    ↓
HudMapAppTheme
    ↓
AppNavigation / NavHost
    ↓
HomeScreen
    ├── HomeMapView → Maps Compose → Maps SDK for Android
    ├── DestinationRepository → Places SDK for Android
    ├── FusedLocationProvider → Play Services Location
    └── HomeViewModel → destination-selection state
```

### Implemented patterns

- **Single-module organization:** package boundaries are conventions within `:app`.
- **Lightweight MVVM:** `HomeViewModel` owns destination-selection state.
- **Repository pattern:** `DestinationRepository` wraps Places access.
- **Provider abstraction:** `LocationProvider` isolates most UI code from the fused-location client.
- **Explicit state models:** destination selection, search, and location status use typed state representations.
- **Composable substitution:** `HomeScreen` accepts a map composable parameter for previews and future UI tests.
- **Reusable presentation:** theme components and the shared HUD navigation layer reduce UI duplication.

### Planned architecture after navigation is added

The route and navigation phases should introduce explicit boundaries rather than placing all asynchronous work in `HomeScreen`:

```text
HomeScreen / HUDScreen
    ↓ collect immutable UI state
NavigationViewModel
    ↓ coordinate commands and state
NavigationRepository / NavigationCoordinator
    ├── Places destination data
    ├── Routes API or Navigation SDK route planning
    ├── Navigation SDK session
    └── Location and sensor providers
            ↓
      NavigationState
            ↓
       Custom HUD
```

The Navigation SDK should own active route following and guidance events. The app should not duplicate its routing engine or infer turn-by-turn guidance only from map polylines.

## 6. Application entry point and manifest

`MainActivity.kt` enables edge-to-edge rendering, installs `HudMapAppTheme`, and starts `AppNavigation()` inside `setContent`.

`AndroidManifest.xml` currently declares:

- `ACCESS_COARSE_LOCATION`.
- `ACCESS_FINE_LOCATION`.
- The Google Maps API-key metadata entry.
- `MainActivity` as the exported launcher activity.
- Backup and data-extraction rule files.

The manifest and Google Cloud configuration will need review when Navigation SDK is introduced. Required permissions, SDK initialization, disclosure text, and navigation-related lifecycle requirements should be implemented from the selected SDK version’s setup guide rather than assumed from Maps SDK alone.

## 7. Current navigation and screens

### Typed routes

`ui/navigation/NavRoutes.kt` defines serializable `AppRoute` objects for:

- `Splash`
- `Introduction1`
- `Introduction2`
- `Introduction3`
- `Home`
- `HUD`
- `MirroredHUD`
- `Settings`

`ui/navigation/AppNavigation.kt` creates a `NavHost` beginning at `Splash`.

### Current screen flow

```text
Splash
  ↓
Introduction1
  ↓
Introduction2
  ↓
Introduction3
  ↓
Home map
  ├── HUD prototype
  │    └── Mirrored HUD prototype
  └── Settings (registered, currently empty)
```

### Home baseline

Home currently provides:

- Google map rendering through `HomeMapView`.
- Current-location display when permission and a location fix are available.
- Recenter control.
- Location permission and status UI.
- Destination autocomplete and place-detail resolution.
- Destination selection bottom sheet.
- Existing top bar and map controls.

Home does **not** currently provide:

- Route requests.
- Route polylines.
- Multiple-route selection.
- Navigation SDK integration.
- Active navigation state.
- Turn-by-turn guidance.

### HUD baseline

`HUDScreen.kt` and `MirroredHUDScreen.kt` are visual prototypes. They render static demonstration values and do not consume a confirmed route, Navigation SDK events, live maneuver data, heading, or progress. This is intentional until the navigation data contract is established.

## 8. Current Home behavior

### Location setup

`HomeScreen` remembers a `FusedLocationProvider`, collects location updates, and manages provider start/stop around lifecycle and permission events. Location UI is provided by:

- `LocationPermissionHandler.kt`
- `LocationPermissionOverlay.kt`
- `LocationPermissionState.kt`
- `FusedLocationProvider.kt`

The current provider exposes both a `callbackFlow` and explicit start/stop methods. The implementation should be unified before navigation begins so one callback path owns location emissions, lifecycle, and cleanup.

### Destination search flow

```text
User types query
    ↓
HomeScreen cancels previous search job
    ↓
300 ms debounce
    ↓
DestinationRepository.searchDestinations()
    ↓
Google Places autocomplete
    ↓
DestinationSearchState.Results / Empty / Error
    ↓
User selects prediction
    ↓
Place details resolve coordinates
    ↓
Destination bottom sheet
```

`DestinationSearch.kt` owns search input, focus, keyboard behavior, clear action, loading state, empty state, error state, and result presentation. `DestinationRepository.kt` uses Places autocomplete and place details.

Autocomplete predictions temporarily use placeholder coordinates until place details complete. Before route work is started, the details-loading state should be made explicit so a destination cannot be confirmed without valid coordinates.

### Destination state transitions

```text
SelectedDestinationState.None
    ↓ select prediction
SelectedDestinationState.Selected(destination)
    ↓ confirm
SelectedDestinationState.Confirmed(destination)
```

This state machine is sufficient for the current map-only baseline. Phase 3 should extend it carefully rather than reintroducing route state directly into the composable.

## 9. Google platform responsibility model

The project should use Google’s products according to their distinct responsibilities.

### Maps SDK for Android and Maps Compose

Use the Maps SDK/Maps Compose for:

- Rendering the interactive base map.
- Camera movement and map gestures.
- Markers and map overlays.
- Displaying route polylines or navigation map content where supported by the selected navigation architecture.
- Map styling and map controls.

The Maps SDK is the map renderer. It is not the application’s turn-by-turn navigation engine.

### Places SDK for Android

Use Places for:

- Autocomplete destination search.
- Place details and coordinates.
- Destination names and addresses.

Places resolves the user’s destination; it does not run an active navigation session.

### Routes API

Use the Routes API for route planning when the product needs to:

- Compute candidate routes before navigation starts.
- Request alternative routes.
- Compare distance, duration, traffic, or route preferences.
- Draw route previews on the map.

The Routes API can calculate route results, but it is not by itself a complete navigation session. It should not be treated as the source of live maneuver progression, rerouting, arrival, or off-route state.

### Navigation SDK for Android

Use Navigation SDK for Android for the active navigation session. It adds navigation features on top of the Maps SDK and is the planned source for:

- Starting and stopping navigation.
- Following a selected destination/route.
- Live navigation progress.
- Current and next maneuver information.
- Distance and time guidance values.
- Arrival and route-status events.
- Off-route handling and rerouting behavior exposed by the SDK.
- Navigation event listeners and navigation-session lifecycle.

The Navigation SDK still uses Maps SDK map concepts and the `com.google.android.gms.maps` APIs for map functionality. It is not a separate replacement for every map composable concern; it is the navigation-capable SDK layer that must be selected and configured when active navigation is implemented.

### Custom HUD

The custom HUD should consume app-owned `NavigationState` derived from Navigation SDK callbacks. It should not scrape text from Google UI or depend on a Google navigation screen being visible.

```text
Navigation SDK events
    ↓
NavigationCoordinator / ViewModel
    ↓
NavigationState
    ↓
HUDViewModel or HUD transformation
    ↓
HUDScreen / MirroredHUDScreen
```

## 10. Refactored product phases

The phases below are dependency-aware and describe deliverables rather than broad aspirations. Phase 2 is the current baseline. Phases 3–5 must be completed in order because the HUD cannot display reliable live guidance before an active Navigation SDK session exists.

### Phase 0 — Architecture and foundation

**Goal:** Establish a maintainable Android foundation.

**Scope:**

- Single-module Kotlin/Compose app.
- Theme, reusable HUD components, typed Navigation Compose routes.
- Initial package boundaries for `data`, `location`, `ui`, and future `domain`/`sensor` work.
- Basic unit and instrumentation test setup.
- Local configuration and API-key handling.

**Exit criteria:**

- App builds from the Gradle wrapper.
- Main activity and navigation graph are stable.
- Sensitive local configuration is excluded from source control.

### Phase 1 — App UI and HUD prototype

**Goal:** Build the visual product language before connecting live navigation data.

**Scope:**

- Splash and onboarding screens.
- Home shell and map-facing layout.
- HUD and mirrored HUD layouts.
- Blue → purple → pink visual language.
- Pitch-black, high-contrast HUD surface.
- Reusable cards, buttons, top bars, bottom bars, loading, and error components.

**Exit criteria:**

- HUD can render static sample states.
- Mirroring is implemented as a reusable presentation mode.
- Navigation between prototype screens works.

### Phase 2 — Google Maps and real location

**Goal:** Provide a stable map-only Home experience.

**Scope:**

- Maps SDK for Android through Maps Compose.
- Google Maps API-key metadata and restricted key configuration.
- Current location permission flow.
- Fused location updates and recentering.
- Map camera, marker, gestures, and map error handling.
- Places autocomplete and place-details resolution for destination selection.

**Current status:** In progress/current baseline. Home shows the map and location experience; route implementation has intentionally been deferred.

**Required cleanup before Phase 3:**

- Unify the two location callback paths.
- Make place-details loading and failure explicit.
- Remove or implement visible no-op map controls.
- Add map and location Compose tests where practical.

**Exit criteria:**

- Home reliably renders a map.
- Location permission denial, disabled services, no fix, and network conditions have clear states.
- A selected destination has valid coordinates before it can be used by later phases.

### Phase 3 — Search, route planning, and navigation foundation

**Goal:** Turn the map into a complete destination-to-navigation entry flow.

**Target flow:**

```text
Search destination
       ↓
Resolve place details
       ↓
Request candidate routes
       ↓
Receive one or more route options
       ↓
Draw preview polylines on the Maps map
       ↓
Select a route
       ↓
Start Navigation SDK session
       ↓
Expose initial guidance state to the app
```

**Responsibilities:**

- Keep Places responsible for destination search and place resolution.
- Choose a route-planning strategy explicitly:
  - Use Navigation SDK route planning if it provides the required route options and selection flow for the selected SDK version; or
  - Use the Routes API for pre-navigation alternative-route previews, then hand the selected destination/route into Navigation SDK for active guidance.
- Do not treat a Routes API response as an active navigation session.
- Add route models and state only when the phase begins; they are intentionally absent from the current baseline.
- Add a route-preview map layer separate from the active-navigation map layer.
- Add a selected-route command and a `startNavigation` command.
- Integrate the Navigation SDK dependency, Cloud setup, initialization, consent/disclosure requirements, and SDK-specific lifecycle.
- Define a stable navigation boundary so HomeScreen does not own raw SDK callbacks.

**Suggested state boundary:**

```text
RoutePreviewState
    Idle | Loading | Available | Empty | Error

NavigationSessionState
    Idle | Starting | Active | Arrived | OffRoute | Stopped | Error
```

**Exit criteria:**

- A valid destination produces one or more real route previews.
- The user can select one route.
- The selected destination/route can start a Navigation SDK session.
- The app receives at least one real guidance/progress event without relying on Google’s default visual UI.

### Phase 4 — Real navigation experience

**Goal:** Make the active navigation session useful and reliable.

**Navigation session responsibilities:**

```text
Navigation SDK session
        │
   ┌────┼──────────────┐
   ↓    ↓              ↓
Maneuver Progress     Status
   │    │              │
   └────┼──────────────┘
        ↓
  NavigationState
        ↓
   Map + HUD + controls
```

**Scope:**

- Navigation start and stop.
- Current maneuver and next maneuver.
- Maneuver instruction and maneuver type.
- Distance to the next maneuver.
- Remaining route distance and time.
- Arrival detection.
- Off-route status and SDK-provided rerouting.
- Route changes and updated guidance.
- Navigation interruptions, background/foreground transitions, and cleanup.
- Session restoration policy after configuration changes or process recreation.
- Stable state updates with no stale callbacks after a session ends.

**Important boundary:**

Navigation SDK owns guidance truth. The app owns presentation state, session commands, and product-specific transformations. The app should not implement a second independent rerouting engine unless a future requirement explicitly demands it.

**Exit criteria:**

- A driver can start, follow, interrupt, resume, and stop navigation.
- The map and app state reflect active navigation status.
- Arrival, off-route, error, and stopped states are visible and testable.

### Phase 5 — Custom HUD 2.0

**Goal:** Drive the product’s custom HUD from real navigation data.

**Data flow:**

```text
Navigation SDK
    ↓
NavigationCoordinator
    ↓
NavigationState
    ↓
HUDState transformation
    ↓
Custom Compose HUD
```

**HUD scope:**

- Large maneuver arrow.
- Distance to maneuver.
- Current instruction.
- Next maneuver preview.
- Remaining distance.
- ETA and remaining time.
- Arrival and rerouting states.
- Animated maneuver transitions.
- Car/position visualization.
- Mirrored HUD presentation.
- High-contrast, low-density information hierarchy.
- Blue → purple → pink product styling on a pitch-black surface.

The HUD should render app-owned state, not Google’s navigation UI. The Navigation SDK supplies navigation machinery and data; Compose owns the final visual design.

**Exit criteria:**

- The HUD updates from a real active navigation session.
- Static mock values are no longer required for the main navigation path.
- Normal and mirrored HUD modes display the same guidance state correctly.

### Phase 6 — Sensors and driving context

**Goal:** Add device context only after navigation data is stable.

**Scope:**

- Compass and heading.
- Device orientation.
- Heading changes and sensor smoothing.
- Speed and movement state from location/navigation data.
- Sensor lifecycle management.
- Camera and heading synchronization where useful.
- Clear fallback behavior when sensors are unavailable or inaccurate.

**Data flow:**

```text
GPS/location ─┐
              ├─ NavigationContext → HUD
Sensors ──────┘
```

Phase 6 is deliberately flexible. Real Navigation SDK data should determine which sensor features improve the HUD instead of building sensors speculatively.

**Exit criteria:**

- Sensor subscriptions start and stop with the active screen/session.
- Heading/orientation data is stable enough for the chosen HUD behavior.
- Missing or unreliable sensor data cannot break navigation.

### Phase 7 — HUD projection and mirroring

**Goal:** Make the HUD suitable for actual windshield projection constraints.

**Scope:**

- Correct horizontal mirroring.
- Projection-safe typography and iconography.
- Brightness and readability considerations.
- Large-distance readability.
- Reduced visual complexity while driving.
- Orientation and landscape handling.
- Full-screen immersive mode.
- HUD-specific animation timing.
- Different normal and mirrored display configurations.

**Exit criteria:**

- Normal and mirrored displays are intentional product modes, not just a flipped canvas.
- Text, arrows, contrast, and spacing remain usable at the intended viewing distance.

### Phase 8 — Settings and user configuration

**Goal:** Make the product configurable after core navigation works.

**Settings areas:**

```text
Settings
├── HUD
│   ├── Mirrored mode
│   ├── Animation intensity
│   └── Information density
├── Navigation
│   ├── Distance unit
│   ├── Voice guidance
│   └── Route preferences
├── Map
│   ├── Map type
│   └── Camera behavior
└── App
    └── General preferences
```

**Scope:**

- Implement the currently empty Settings screen.
- Replace the Home settings-action/HUD navigation mismatch.
- Use DataStore for preferences that must survive process restarts.
- Keep settings state independent of transient navigation-session state.

**Exit criteria:**

- Settings affect the relevant UI or navigation behavior.
- Preferences restore after process restart.
- Unsupported options are not exposed as inactive controls.

### Phase 9 — Reliability, testing, and polish

**Goal:** Make the navigation product resilient under real conditions.

**Scope:**

- Loading, empty, and error states for Places, route planning, Maps, and Navigation SDK.
- GPS unavailable and permission changes.
- Network loss and recovery.
- Navigation interruptions and app backgrounding.
- Rotation/configuration changes and process recreation.
- API-key/configuration failures.
- Route calculation and Navigation SDK failures.
- Resource cleanup and battery behavior.
- Request cancellation and stale-response protection.
- Structured, user-safe errors with diagnostic logging.

**Testing layers:**

```text
Unit tests
    +
Repository and route-mapping tests
    +
Navigation state/session tests
    +
Compose UI tests
    +
Navigation graph tests
    +
Error and lifecycle tests
```

**Exit criteria:**

- Core navigation state transitions are covered.
- The app has deterministic behavior for permission, network, route, arrival, off-route, and interruption cases.
- CI runs formatting/build/unit-test checks and reports failures clearly.

### Phase 10 — Production and release

**Goal:** Prepare a distributable Android product.

**Scope:**

- Release build configuration.
- Signing configuration.
- R8/ProGuard review and optimization.
- API-key restrictions and service separation.
- Secrets and environment configuration.
- Versioning and changelog discipline.
- App icon and splash polish.
- Permission and privacy review.
- Crash/error monitoring.
- Release-device and real-driving validation.
- Play Store preparation.

**Target pipeline:**

```text
GitHub pull request
        ↓
CI checks and tests
        ↓
develop
        ↓
Release preparation
        ↓
main
        ↓
Signed release build
        ↓
Google Play
```

**Exit criteria:**

- A signed, reproducible release artifact can be built.
- Required Google APIs and keys are production-restricted.
- Privacy, permissions, navigation disclosures, monitoring, and store materials are ready.

## 11. State ownership roadmap

### Current state

- **HomeViewModel:** destination-selection state.
- **HomeScreen:** search query/results, debounce job, permission overlay reason, network snapshot, recenter event, banner dismissal, and effects.
- **FusedLocationProvider:** platform callbacks and location status.
- **HUD screens:** static presentation state.

### Phase 3 target

Introduce a navigation state-holder rather than adding route flows directly to HomeScreen:

```text
HomeViewModel
    ├── DestinationSearchState
    ├── SelectedDestinationState
    └── RoutePreviewState

NavigationViewModel / Coordinator
    ├── NavigationSessionState
    ├── ManeuverState
    ├── ProgressState
    └── NavigationError
```

The exact class split can remain flexible, but raw Places, Routes, and Navigation SDK callbacks should not be scattered across multiple composables.

### Phase 5 target

Define the minimum app-owned HUD contract:

```text
data class NavigationState(
    val sessionStatus: SessionStatus,
    val currentManeuver: Maneuver?,
    val nextManeuver: Maneuver?,
    val distanceToManeuverMeters: Int?,
    val remainingDistanceMeters: Int?,
    val remainingDurationSeconds: Long?,
    val eta: Instant?,
    val isRerouting: Boolean,
    val error: NavigationError?
)
```

The final fields must follow the actual Navigation SDK callbacks and available data. This model is a design target, not an instruction to invent values unavailable from the SDK.

## 12. Testing and quality status

Current tests are limited to template-level checks and the existing map/location-era code. There are no active route or navigation implementation files in the current baseline.

Important future coverage:

- `HomeViewModel` destination transitions.
- Places autocomplete and details mapping.
- Location permission and provider lifecycle.
- Route-preview mapping and selection when Phase 3 begins.
- Navigation session commands and event mapping.
- Arrival, off-route, rerouting, interruption, and stop states.
- HUD rendering for each `NavigationState`.
- Mirrored HUD presentation.
- Navigation graph and restoration behavior.
- Malformed or missing SDK/API data.

No visible coverage tool or minimum coverage threshold is currently configured.

## 13. Security and operational notes

- Android API keys are client credentials, not server secrets.
- Restrict keys by package name and signing certificate.
- Enable only the Google APIs/SDKs needed by the current phase.
- Consider separate restrictions or keys for Maps, Places, Routes, and Navigation SDK usage where the Google Cloud product configuration permits.
- Configure quotas and billing alerts before route and navigation testing.
- Do not log full SDK/API responses if they contain sensitive location data.
- Treat precise location, destinations, routes, and navigation history as sensitive product data if persistence or telemetry is added.
- Review Google Maps Platform terms, attribution, consent, privacy, and navigation-specific requirements before distribution.

## 14. Current limitations and next implementation priorities

### Current limitations

- Route implementation is intentionally absent after the map-only rollback.
- Navigation SDK is not integrated.
- HUD uses static values.
- Sensors are not implemented.
- Settings is empty.
- Onboarding completion is not persisted.
- Location update ownership is split between flow collection and explicit callbacks.
- Place-detail loading and errors need a stronger state model.
- Production UI and navigation behavior have limited automated coverage.

### Recommended next order

1. Stabilize Phase 2 map/location behavior and update tests.
2. Confirm the exact Navigation SDK version, supported Android requirements, Google Cloud products, billing, and licensing/terms.
3. Design the Phase 3 route-preview and Navigation SDK boundary before adding route files again.
4. Implement destination → route preview → route selection with explicit state and cancellation.
5. Start a Navigation SDK session from the selected destination/route.
6. Map real SDK events into `NavigationState`.
7. Connect `NavigationState` to the custom HUD.
8. Only then decide which sensors and projection features materially improve the product.

## 15. Official Google documentation references

- [Google Maps Platform documentation](https://developers.google.com/maps/documentation)
- [Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk)
- [Navigation SDK for Android](https://developers.google.com/maps/documentation/navigation/android-sdk)
- [Navigation SDK reference overview](https://developers.google.com/maps/documentation/navigation/android-sdk/reference)
- [Routes API](https://developers.google.com/maps/documentation/routes)
- [Places SDK for Android](https://developers.google.com/maps/documentation/places/android-sdk)

## 16. File reference index

### Entry point and build

- `app/src/main/java/com/example/hudmapapp/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `.github/workflows/android-ci.yml`

### Navigation

- `app/src/main/java/com/example/hudmapapp/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/example/hudmapapp/ui/navigation/NavRoutes.kt`

### Current Home feature

- `app/src/main/java/com/example/hudmapapp/ui/screens/homeScreen/HomeScreen.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/homeScreen/HomeViewModel.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/homeScreen/HomeMapView.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/homeScreen/DestinationSearch.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/homeScreen/DestinationBottomSheet.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/homeScreen/MapPlaceholder.kt`

### Current data and location

- `app/src/main/java/com/example/hudmapapp/data/model/Destination.kt`
- `app/src/main/java/com/example/hudmapapp/data/model/DestinationSearchState.kt`
- `app/src/main/java/com/example/hudmapapp/data/model/SelectedDestinationState.kt`
- `app/src/main/java/com/example/hudmapapp/data/repository/DestinationRepository.kt`
- `app/src/main/java/com/example/hudmapapp/location/AppLocation.kt`
- `app/src/main/java/com/example/hudmapapp/location/LocationProvider.kt`
- `app/src/main/java/com/example/hudmapapp/location/FusedLocationProvider.kt`
- `app/src/main/java/com/example/hudmapapp/location/LocationPermissionState.kt`
- `app/src/main/java/com/example/hudmapapp/location/LocationPermissionHandler.kt`
- `app/src/main/java/com/example/hudmapapp/location/LocationPermissionOverlay.kt`

### Other screens

- `app/src/main/java/com/example/hudmapapp/ui/screens/splashScreen/SplashScreen.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/splashScreen/SplashScreenLayout.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/introductionScreens/IntroductionScreen1.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/introductionScreens/IntroductionScreen2.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/introductionScreens/IntroductionScreen3.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/hudScreen/HUDScreen.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/hudScreen/MirroredHUDScreen.kt`
- `app/src/main/java/com/example/hudmapapp/ui/screens/settingScreen/SettingScreen.kt`

### Theme and architecture notes

- `app/src/main/java/com/example/hudmapapp/ui/theme/`
- `app/src/main/java/com/example/hudmapapp/ARCHITECTURE.md`

### Tests

- `app/src/test/java/com/example/hudmapapp/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/example/hudmapapp/ExampleComposeTest.kt`
- `app/src/androidTest/java/com/example/hudmapapp/ExampleInstrumentedTest.kt`

This document describes the reviewed implementation and roadmap as of 2026-09-19. Update it when the Navigation SDK version, route-planning strategy, data ownership, API contracts, persistence, HUD behavior, or build requirements change.
