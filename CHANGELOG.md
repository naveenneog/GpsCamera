# Changelog

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
