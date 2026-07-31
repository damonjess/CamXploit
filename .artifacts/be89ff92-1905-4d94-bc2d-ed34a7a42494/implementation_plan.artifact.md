# Implementation Plan - OSINT Dashboard & Navigation Refactor

This plan refactors the Global OSINT feature into a dedicated persistent dashboard and improves the application's navigation structure to better categorize intelligence and tools.

## User Review Required

> [!IMPORTANT]
> The current application has 9 tabs. The proposed 5-item `NavigationBar` would require reorganizing or nesting some existing features (Console, Archive, Stream, Saved). I will aim to maintain all functionality while adopting the new Navigation structure.

## Proposed Changes

### OSINT Module

#### [MODIFY] [OsintViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/OsintViewModel.kt)
- Refactor to extend `AndroidViewModel`.
- Integrate `DataStore` to persist the Shodan API key.
- Update `setApiKey` to save the key to disk.
- Update `init` to load the key automatically.

#### [NEW] [OsintScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/OsintScreen.kt)
- Create a full-screen version of the OSINT dashboard.
- This will be the primary content for the `INTEL` tab.

#### [MODIFY] [GlobalOsintSheet.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/GlobalOsintSheet.kt)
- Refactor to share UI components with `OsintScreen`.

### Navigation & UI

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Replace the current `IntelTab` content with the new `OsintScreen`.
- Refactor the bottom navigation to follow the 5-item `NavigationBar` pattern if appropriate, or optimize the existing `ScrollableTabRow` to prioritize the new items.
- Ensure the Globe icon in the header remains as a quick shortcut.

## Verification Plan

### Automated Tests
- `gradle_build` to verify no compilation errors, especially with `DataStore` and new `AndroidViewModel` structure.

### Manual Verification
- Enter an API key in the OSINT tab, restart the app, and verify it persists.
- Navigate to the `INTEL` tab and verify the full dashboard is functional.
- Tap the Globe icon from other tabs to verify the quick overlay still works.
