# Project Architecture

The application follows a feature-oriented layered structure.

## Package Structure

- `data` — Data sources, repositories, and data models.
- `domain` — Core business/domain models and logic.
- `location` — Location and GPS-related functionality.
- `sensor` — Device sensor functionality.
- `ui` — Jetpack Compose presentation layer.
- `ui.components` — Reusable UI components.
- `ui.navigation` — Navigation routes and navigation graph.
- `ui.screens` — Application screens.
- `ui.theme` — Application theme, colors, and typography.

## Principles

- Keep responsibilities separated by package.
- Avoid creating abstractions before they are needed.
- Keep UI logic inside the UI layer.
- Keep location and sensor handling isolated from UI code.
- Keep domain models independent of Android UI concerns.