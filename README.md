# TileDeck Widgets

An unofficial, widget-only Android app that logs into your college's Etlab
portal (e.g. `lbscek.etlab.app`) and surfaces attendance as a live home-screen
widget — refreshed automatically in the background.

Not affiliated with Etlab, Etuwa Concepts, or any institution.

## Screenshots
<img width="300" alt="Screenshot" src="https://github.com/user-attachments/assets/564b6582-9fdc-4bd4-9ba1-2e9e28e4a8a6" />


## How it works
- **Login**: authenticates against Etlab's mobile/app API using your
  username/password (no HTML form replay).
- **Fetch**: calls Etlab's app API endpoints directly for attendance data
  (no HTML scraping).
- **Storage**: credentials + last fetched results are kept in
  `EncryptedSharedPreferences`, on-device only.
- **Refresh**: a WorkManager job re-fetches periodically; you can also tap
  **Refresh now** on the widget.
- **Widget**: built with Jetpack Glance (Compose for App Widgets), tile-based
  layouts sized for the home screen.

## Widgets
Each widget shows one slice of your data at a glance. Currently shipped:

- **Attendance** — per-subject and overall percentage

## Todo
Planned tile types (implementation would follow the same pattern: a Glance
widget + a matching data source in the repository layer):

- [ ] **Marks** — internal / series exam marks
- [ ] **Timetable** — today's or the current slot's classes
- [ ] **Overview** — a compact combined tile

## Setup
1. Open this folder in **Android Studio** (Giraffe or newer). It will
   generate the Gradle wrapper automatically on first sync.
2. Let Gradle sync — it needs internet access to `google()` and
   `mavenCentral()` the first time, to pull dependencies.
3. Run the app on a device/emulator (min SDK 26).
4. Enter your Etlab username/password and your current semester, then tap
   **Save & fetch**.
5. Long-press your home screen → Widgets → **TileDeck** → drag the
   Attendance widget on.

## Project layout
```
app/src/main/java/.../
  data/     EtlabRepository (login + API fetch), encrypted prefs, models
  work/     Periodic background refresh (WorkManager)
  widget/   Glance widgets + widget receivers
  ui/       MainActivity (setup / credentials screen)
```

## Naming note
The app title is **TileDeck Widgets**. Etlab is referenced only as a
compatibility target — never in the app title, package name, or launcher
label — to avoid trademark issues on the Play Store.
