# Changelog

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
