# Implementation Plan - OSINT Module Refinement & Public Cams UI

This plan aims to integrate the user-provided "leak-free" and "UI-safe" improvements for the OSINT module, specifically focusing on the `InsecamScraper` and the `PublicCamsPanel`. It also addresses the migration from `ZoomEye` to `Censys` as indicated in the provided `OsintViewModel` snippet.

## User Review Required

> [!IMPORTANT]
> The provided `OsintViewModel.kt` code switches the OSINT source from `ZoomEye` to `Censys`, but the `CensysClient` and `CensysPanel` implementations were not provided.
>
> **I will proceed by:**
> 1. Implementing the `InsecamScraper` and `PublicCamsPanel` exactly as provided.
> 2. Updating `OsintViewModel` to include the new `Insecam` logic and the `Censys` state/logic.
> 3. **Adding a new `CensysClient.kt`** and a basic `CensysPanel` in `OsintScreen.kt` to ensure the project compiles and functions with the new `Censys` source.
> 4. Removing `ZoomEye` references from the `Source` selector to match the provided `OsintViewModel` structure.

> [!WARNING]
> The new `PublicCamsPanel.kt` removes the `DirectStreamPanel()` which was present in the original version. I will follow the user's provided code and remove it.

## Proposed Changes

### [OSINT Core]

#### [MODIFY] [InsecamScraper.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/InsecamScraper.kt)
- Update to the provided version using `applicationContext` and `Handler(Looper.getMainLooper())` for thread-safe WebView interactions.
- Refine the extraction JavaScript.

#### [MODIFY] [OsintViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/OsintViewModel.kt)
- Replace `ZoomEye` source with `Censys`.
- Implement `initScraper()`, `loadCountries()` (with hardcoded list), and `loadInsecamCountry()`.
- Ensure `onCleared()` destroys the scraper.

#### [NEW] [CensysClient.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/CensysClient.kt)
- Create a new client to handle Censys host search API requests using OkHttp.
- Define `CensysClient.Host` data class to match the `OsintViewModel` requirements.

---

### [UI Components]

#### [MODIFY] [PublicCamsPanel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/PublicCamsPanel.kt)
- Update to the provided version with the camera grid and country list.
- Integrate `CountryRow` and `CameraThumbnailCard` as private composables.

#### [MODIFY] [OsintScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/OsintScreen.kt)
- Update the `Source` chips to show `CENSYS` instead of `ZOOMEYE`.
- Route the `Censys` source to a new `CensysPanel`.
- Implement a basic `CensysPanel` (similar to the previous `ZoomEyePanel` but for Censys fields).

## Verification Plan

### Automated Tests
- Build the project using `gradle_build("app:assembleDebug")` to verify compilation.

### Manual Verification
1.  **Public Cams Tab:**
    - Verify the country list appears.
    - Tap a country (e.g., US) and verify camera thumbnails load.
    - Tap a camera and verify it opens the stream viewer (WebView mode).
2.  **Censys Tab:**
    - Verify the API ID and Secret fields are present.
    - Test saving/loading these credentials.
3.  **Leak Prevention:**
    - Verify no crashes occur when navigating away from the OSINT screen or rotating the screen (handled by `onCleared` and `applicationContext`).
