# Implementation Plan - OSINT Refinement & Leak Prevention

Refine the `InsecamScraper` to be UI-safe and prevent context leaks, and update the OSINT module to use a more robust, native-feeling UI for public cameras.

## User Review Required

> [!IMPORTANT]
> The provided `OsintViewModel` snippet switched from `ZoomEye` to `Censys`. Since `CensysClient` does not exist in the project, I will maintain the `ZoomEye` implementation while integrating the requested `Insecam` improvements (leak prevention, hardcoded country list).

> [!NOTE]
> I will integrate `CountryRow` as a private component within `PublicCamsPanel.kt` to follow the user's latest structure and delete the standalone `CountryRow.kt` created earlier.

## Proposed Changes

### [OSINT Core]

#### [MODIFY] [InsecamScraper.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/InsecamScraper.kt)
- Use `applicationContext` to avoid leaking activity context.
- Ensure all `WebView` interactions (creation, loading, destruction) occur on the Main thread using `Handler(Looper.getMainLooper())`.
- Remove `loadCountries()` as the list will now be hardcoded in the ViewModel to improve reliability.
- Update the extraction JavaScript regex for better compatibility.

#### [MODIFY] [OsintViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/OsintViewModel.kt)
- Update `initScraper()` to use the application context and handle initialization state correctly.
- Implement `loadCountries()` with a hardcoded list of top countries to bypass Cloudflare hurdles on the country list page.
- Add `InsecamCountry` data class.
- Override `onCleared()` to properly destroy the scraper.

---

### [UI Components]

#### [MODIFY] [PublicCamsPanel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/PublicCamsPanel.kt)
- Re-implement with the updated structure provided by the user.
- Use `SubcomposeAsyncImage` for camera thumbnails with explicit loading and error UI.
- Integrate `CountryRow` as a private composable at the bottom of the file.

#### [DELETE] [CountryRow.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/CountryRow.kt)
- This component is now internal to `PublicCamsPanel.kt`.

## Verification Plan

### Automated Tests
- Build the project using `gradle_build("app:assembleDebug")`.

### Manual Verification
1.  Open **PUBLIC CAMS**.
2.  Verify the hardcoded country list appears instantly.
3.  Tap a country and verify thumbnails load with the new `SubcomposeAsyncImage` progress indicator.
4.  Verify that navigating back from a country works correctly.
5.  Check for any "Only the original thread that created a view hierarchy can touch its views" errors in Logcat during scraper lifecycle events.
