# Phase 3–4 Test Notes — Route Preview, Session & Guidance Verification

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
| Final/non-final arrival, mid-session reroute chain, stale arrival, double stop, feed bracketing | `NavigationSessionTest` | `arrivalTarget.onArrival(Boolean)` — real `ArrivalEvent` cannot be constructed on JVM (final class, native `Waypoint`) |
| Guidance mapping, maneuver changes, fallbacks, user-safe messages | `NavigationStateTest` | `fakeGuidance()` snapshots |
| Feed ticks, stop-clears-feed, null-snapshot fallback, simulator delegation | `NavigationStateTest` | Scripted `GuidanceSnapshot` sequences via fake feed |
| Sheet loading / empty / error / selection / confirm | `RoutePreviewSheetTest` (androidTest) | Compose rule with canned models |
| Banner Active / Rerouting / OffRoute / Interrupted / Error, Retry / Resume / Stop callbacks | `NavigationSessionBannerTest` (androidTest) | Compose rule with canned session + guidance state |
| Shared location registration, cleanup, observer independence, replay, stale callbacks, failures, last-fix ordering | `location/SharedLocationProviderTest` | Android-free source fake; no Play Services or API key |
| Optional bearing/speed metadata and platform availability mapping | `location/AppLocationTest` | Proves missing is `null` while valid zero remains available |
| Phase 6 heading/speed normalization and immutable context defaults/equality | `domain/driving/DrivingContextStateTest` | Contract-only coverage; no sensors, fusion, or HUD behavior |

## 4.1 live guidance feed — approach decision

`registerServiceForNavUpdates` exists in 7.6.1 but is marked Preview, requires a
bound `Messenger` service, and delivers `NavInfo` bundles only to that service —
heavy machinery for an in-app HUD feed. The implemented approach is a main-thread
2s poll (`FEED_INTERVAL_MILLIS`, 1s initial delay) calling `readGuidance()` on
the active session only:

- `readGuidance()` first tries turn-by-turn `NavInfo` mapping (`mapNavInfo` via
  reflection over `getCurrentStep` / `getRemainingSteps` /
  `getDistanceToCurrentStepMeters` / `getTimeToFinalDestinationSeconds` /
  `getRouteChanged`), then falls back to `getTimeAndDistanceList()`.
- Reflection (not direct references) keeps `turnbyturn` out of the compile
  boundary: if Google renames those getters, mapping returns null and the
  fallback applies instead of crashing.
- Ticks are sequence-guarded and ignored unless the session is Active /
  Rerouting / OffRoute; the feed stops on stop / arrival / clear.
- `startSimulator(speedMultiplier)` wraps
  `navigator.simulator.simulateLocationsAlongExistingRoute()` for emulator runs:
  start a session, call `coordinator.startSimulator()`, and the same poll feeds
  real SDK snapshots without driving.
- Battery note: 2s main-thread reads of two SDK getters is cheaper than a
  1Hz service + IPC; revisit if HUD needs sub-second maneuver flips.

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

## Simulator-based emulator verification (no driving needed)

1. Build and install on a Play-Services emulator image:
   `gradlew installDebug`.
2. Grant location permission; in extended controls set a route (e.g. two points
   ~10 km apart) and confirm the destination so route previews load.
3. Start navigation, then trigger the simulator from an `adb shell` debug hook
   or a temporary debug button calling
   `sessionCoordinator.startSimulator(5f)` — the SDK replays locations along
   the existing route at 5x.
4. Watch the banner: maneuver instruction, distance-to-maneuver, remaining
   distance / ETA update every ~2 s from the same `readGuidance()` path as a
   real drive.
5. Background the app 30+ s: banner pauses to Interrupted; foreground resumes.
6. `stopSimulator()` is not exposed in UI — ending the session via Stop also
   ends simulation when destinations clear.

## HUD simulator verification (Phase 5, no driving needed)

Same simulator session as above, but navigate Home → HUD after step 3:

1. HUD shows live instruction, distance-to-maneuver, `Then …` next preview,
   and remaining / ETA footer ticking every ~2 s — identical values to the
   banner (same `navigationState`).
2. From an `adb shell` hook, drive the coordinator into `Rerouting` /
   `OffRoute`: HUD shows "Finding a better route." / "Off route. Finding a
   new route." with frozen last-known guidance (no torn mix).
3. Background 30+ s: HUD shows the paused message with Resume; tap Resume.
4. End the session via Stop: HUD falls back to "No active navigation".
5. Open MirroredHUD: same guidance state renders mirrored (geometric flip —
   correct in windshield reflection by design, see `MirroredHUDScreen` KDoc).
6. Rotate and jump Home ↔ HUD ↔ MirroredHUD: one session, no duplicates,
   no leaked listeners in logcat.

## Manual / live-device checklist (not in CI)

### Route preview (Phase 3)

- [ ] Real device with Play Services: confirm destination → routes render on map.
- [ ] Select each candidate route: highlight + sheet state stay in sync.
- [ ] Deny location permission: SDK init error surfaces typed error, no crash.
- [ ] Invalid key / Routes API disabled: classified auth error, retry works.

### Active session (Phase 4)

- [ ] Start: real guidance begins, banner shows maneuver + distance + ETA.
- [ ] Follow: values update while moving; maneuver advances at turns.
- [ ] Off-route: banner shows recalculating, recovers to Active with new maneuver.
- [ ] Airplane mode mid-session: interruption appears, resume works on reconnect.
- [ ] Arrival: arrived state, no further updates, listeners released.
- [ ] Stop mid-session: Stopped state, map usable, no leaks in logcat.
- [ ] Rotation during Active: session survives, no duplicate.
- [ ] Background 30 s then return: pauses per #55 policy, resumes on return.
- [ ] Permission revoked mid-session: typed error, no crash.

### Custom HUD (Phase 5)

- [ ] Start: HUD shows real maneuver + distance + ETA, matches banner.
- [ ] Follow: values update while moving; maneuver flips animate at turns.
- [ ] Off-route: HUD recalculating, recovers to Active with fresh maneuver.
- [ ] Airplane mode mid-session: interruption + Resume works on reconnect.
- [ ] Arrival: frozen arrived state, no further updates.
- [ ] Normal vs mirrored: identical guidance, readable at arm-plus length in low light.
- [ ] Rotation + Home <-> HUD <-> MirroredHUD jumps: no duplicate session, no leak in logcat.

## Standard commands

```text
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

CI (`.github/workflows/android-ci.yml`) runs `assembleDebug` + `test` on
`develop` pushes/PRs; `connectedDebugAndroidTest` needs an emulator or device
and runs locally.
