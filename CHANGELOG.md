# Changelog

## v1.1.2 — 2026-07-17

Video + camera release. Recorded videos are now geotagged and appear in the in-app gallery, and you can switch between the front and back cameras.

### Fixed
- **Recorded videos are now geotagged.** The current GPS fix is embedded into each MP4's location metadata (ISO-6709), so videos map just like photos.
- **Videos now show up in the in-app gallery.** The gallery reads both `Pictures/GPSCamera` (photos) and `Movies/GPSCamera` (videos); video cells get a play badge and open in an in-app player, with **Open in Maps** read from the video's embedded location and **Share**.

### Added
- **Front / back camera switch** — a one-tap lens toggle on the camera screen (both portrait and landscape); disabled mid-recording.

## v1.1.1 — 2026-07-14

Reliability + design release. Redesigns the burned-in stamp to a clean "map-on-the-left" card and makes the mini-map embed reliably on real devices.

### Fixed
- **Mini-map now always embeds on captured photos.** Map tiles are fetched in parallel with retries/backoff, an identifying User-Agent (per the OSM tile usage policy), a 7-day on-disk tile cache with revalidation, and a prefetch so the map is ready at the shutter — even on flaky mobile networks.

### Changed
- **Redesigned geotag stamp** to match the popular "GPS Map Camera" style, in both portrait and landscape:
  - Square **map thumbnail on the left** with a "Maps" tag and OpenStreetMap attribution.
  - **Bold locality line** (e.g. *Bengaluru South, Karnataka, India*) above the full wrapped address.
  - Coordinates shown as **`Lat 12.978361 , Long 77.599380`**.
  - Date/time as **`MM/DD/YY hh:mm AM/PM`** and a **`Note : Capture by GPS Camera`** line.
  - Small **app-logo, country-flag and live-temperature** chips in the corner.

### Added
- **Live temperature** on the stamp via the keyless [Open-Meteo](https://open-meteo.com/) API (best-effort, cached, never blocks capture).

## v1.1.0 — 2026-07-05

Feature + fix release based on real-device feedback.

### Added
- **Pinch-to-zoom** on the camera preview, with a live zoom indicator.
- **Video recording** — a Photo/Video mode switch; videos are saved to `Movies/GPSCamera` (with audio when the mic permission is granted).
- **Full-screen photo viewer** — tap any gallery thumbnail to open it, swipe between shots, pinch-zoom, plus **Open in Maps** (from the photo's EXIF) and **Share**.
- **Landscape support** — the app now rotates; controls reflow to a right-side rail so nothing overlaps.
- In-app gallery now shows the whole `Pictures/GPSCamera` album (optional `READ_MEDIA_IMAGES`), so it survives reinstalls.

### Changed
- The burned-in stamp now shows a **single coordinate format** (decimal) and the **reverse-geocoded address** instead of two coordinate formats.
- The mini-map is now guaranteed on every captured photo (fetched at capture time if not already cached).

## v1.0.0 — 2026-07-04

First public release.

### Added
- Native Kotlin + Jetpack Compose + CameraX GPS camera.
- Burned-in geotag stamp: DMS + decimal coordinates, altitude, accuracy, reverse-geocoded address and timestamp.
- Live OpenStreetMap mini-map on the viewfinder and on the saved photo.
- Tap the mini-map or the map button to open the exact location in Google Maps.
- Drag-to-move and pinch-to-resize the info block before capture.
- Standards-compliant GPS EXIF (lat/lon/alt/timestamp) plus an embedded Google Maps URL.
- Photos saved to a dedicated `Pictures/GPSCamera` album with an in-app gallery.
- Day/night theme toggle (defaults to the system setting).
- Resilient location that merges Play Services fused location with the platform GPS/network providers.
- Comprehensive unit + instrumented test suite.
- App icon generated with Azure AI Foundry `gpt-image-2`.
