# Phase 3 Test Notes — Route Preview & Navigation Session Foundation

Automated coverage lives in `app/src/test` (JVM unit) and `app/src/androidTest`
(Compose). Everything runs with the project's standard commands and requires
no API key or live Google service.

## What is covered automatically

| Area | Test file | Notes |
|---|---|---|
| Route mapping, coordinate validation, empty + classified failures | `RoutePreviewTest` | Fake `RoutePlanningDataSource`; real `RoutesApiDataSource.parseRoutes` + polyline decoder on canned JSON |
| Stale-response + cancellation | `RoutePreviewTest` | Sequence guard via delayed fake provider |
| Destination select → confirm → preview → select → clear | `HomeViewModelTest` | Fake repository through `RoutePreviewCoordinator` |
| Session start, duplicate prevention, stop, cleanup, stale callback | `NavigationSessionTest`, `NavigationInitTest` | `FakeNavigatorAdapter` / `InitFakeAdapter` |
| Rerouting, off-route, interruption, resume, retry, arrival/stop freeze | `NavigationSessionTest` | Fake SDK event firing |
| Guidance mapping, maneuver changes, fallbacks, user-safe messages | `NavigationStateTest` | `fakeGuidance()` snapshots |
| Sheet loading / empty / error / selection / confirm | `RoutePreviewSheetTest` (androidTest) | Compose rule with canned models |

## Navigation SDK integration assumptions (fakes stand in for these)

1. `Navigator.setDestinations()` resolves its `ListenableResultFuture<RouteStatus>`
   exactly once per call.
2. `startGuidance()` after `RouteStatus.OK` leads to `isGuidanceRunning() == true`.
3. `ArrivalListener.onArrival()` fires with `isFinalDestination == true` once per
   destination; `continueToNextDestination()` is single-destination out of scope.
4. `RouteChangedListener`, `RemainingTimeOrDistanceChangedListener`,
   `ReroutingListener.onReroutingRequestedByOffRoute()` fire only while a session
   is active; listeners removed on stop/arrival never fire again.
5. `getRouteSegments()` / `getTimeAndDistanceList()` return positions aligned to
   the current destination; empty lists mean "no guidance yet", not an error.
6. Route tokens from Routes API `computeRoutes` are accepted by
   `setDestinations(waypoints, CustomRoutesOptions)` for the same waypoints.
7. Turn-by-turn `Maneuver` int codes are stable across SDK minor versions; unknown
   codes map to `ManeuverType.UNKNOWN` rather than failing.

## Manual / live-device checklist (not in CI)

- [ ] Real device with Play Services: confirm destination → routes render on map.
- [ ] Select each candidate route: highlight + sheet state stay in sync.
- [ ] Start navigation: real guidance begins, banner shows destination + summary.
- [ ] Drive off-route: banner shows recalculating, then recovers to active.
- [ ] Airplane mode mid-session: interruption state appears, resume works.
- [ ] Arrive at destination: arrived state, no further guidance updates.
- [ ] Stop mid-session: listeners released (no leak warnings in logcat).
- [ ] Deny location permission: SDK init error surfaces typed error, no crash.
- [ ] Invalid key / Routes API disabled: classified auth error, retry works.

## Standard commands

```text
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

CI (`.github/workflows/android-ci.yml`) runs `assembleDebug` + `test` on
`develop` pushes/PRs; `connectedDebugAndroidTest` needs an emulator or device
and runs locally.
