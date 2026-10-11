# Project Architecture

The application follows a feature-oriented layered structure:

Google SDKs produce data → the app owns immutable state → Compose renders it.

## Package Structure

- `data.model` — App-owned data models (`Destination`, `RoutePreview`, …).
- `data.repository` — Google boundaries: Places (`DestinationRepository`),
  Routes API over HTTPS (`RoutePlanningRepository`), route logging.
- `domain.model` — Platform-independent domain models.
- `domain.driving` — Immutable normalized driving-context vocabulary.
- `location` — Fused location stack: permission handling, shared update
  provider, typed fix model, banners/overlays for location state.
- `navigation` — Navigation SDK boundary and app-owned guidance state:
  `NavigationManager` (navigator lifecycle), `NavigationSessionCoordinator`
  (session state machine), `NavigatorAdapter` (SDK adapter seam),
  `TurnByTurnService`/`TurnByTurnNavStore`/`TurnByTurnMapper` (SDK
  turn-by-turn feed), `GuidanceMapper` (snapshot → `NavigationState`).
- `ui` — Jetpack Compose presentation layer.
- `ui.navigation` — Typed routes and the navigation graph.
- `ui.screens` — Application screens (homeScreen, hudScreen, …).
- `ui.theme` — Theme, colors, typography, shared components.

## Location and Driving Context

```text
FusedLocationProviderClient
    → FusedLocationUpdateSource
    → SharedLocationProvider
    → passive app-owned flows
    → UI and navigation consumers
```

Collectors never register platform callbacks; the active lifecycle owner
calls `startUpdates()` and `stopUpdates()`. `domain.driving` defines
immutable context contracts without Android, Google SDK, or Compose types.
Sensor producers and source fusion remain separate work.

## Navigation state flow

```text
NavigationApi (Application-scoped)
    → NavigationManager (init lifecycle, terms/permission gating)
    → NavigationSessionCoordinator (session machine + listeners + poll)
    → NavigatorAdapter → TurnByTurnService → GuidanceSnapshot
    → GuidanceMapper → NavigationState
    → Home banner / HUD screen
```

## Principles

- Keep responsibilities separated by package.
- Avoid creating abstractions before they are needed.
- Keep UI logic inside the UI layer.
- Keep Google SDK calls inside the data/navigation seams, never in the UI.
- Keep location and sensor handling isolated from UI code.
- Keep domain models independent of Android UI concerns.
- Google and Android APIs produce raw data; the app owns immutable state;
  Compose renders it.
