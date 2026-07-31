# Implementation Plan - Fix Screenshot Display, PDF Reporting, and ZoomEye API Errors

The user reported an issue after taking a screenshot, which shows an "Insufficient credits" error in the ZoomEye search panel, despite having points. Additionally, research revealed that the screenshot feature doesn't update the "LAST SNAPSHOT" preview, and PDF reporting is poorly implemented.

## User Review Required

> [!IMPORTANT]
> The "Insufficient credits" error (402) from ZoomEye usually indicates that the account's quota for the specific API endpoint has been reached. Even if points are displayed, they might be "Web" points rather than "Host" points, or the plan might have daily limits. I will improve the API request parameters to ensure maximum compatibility, but a real account with sufficient quota is still required for the service to work.

## Proposed Changes

### 1. **Core UI & Screenshot Fixes**

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Update `captureScreenshot` to accept a callback `(Bitmap) -> Unit` so the UI state can be updated.
- Modify the `IconButton` in `CamGuardianApp` to pass a lambda that updates `capturedBitmap`.
- Improve `generatePdfReport` to handle multi-line text by splitting the content and drawing it line-by-line.

### 2. **ZoomEye API Improvements**

#### [MODIFY] [ZoomEyeClient.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/ZoomEyeClient.kt)
- Add `sub_type = "v4"` to the search JSON body to explicitly request IPv4 host data.
- Explicitly add the `Content-Type: application/json` header to the search request.
- Update `getUserInfo` to also fetch `search_points` if available, or clarify the points display.

#### [MODIFY] [OsintViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/OsintViewModel.kt)
- Improve error handling to distinguish between "Out of Points" and "Unauthorized" or "Rate Limited".

### 3. **Osint UI Tweaks**

#### [MODIFY] [OsintScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/OsintScreen.kt)
- Make the ZoomEye error message clearer and provide a "Check Points" button that refreshes the balance.

## Verification Plan

### Automated Tests
- I will check if the project builds successfully after the changes.
- (Optional) Run `HttpFingerprinterTest.kt` if applicable to ensure no regressions in core logic.

### Manual Verification
1. **Screenshot:** Tap the camera icon in the top bar and verify that the "LAST SNAPSHOT" section appears at the bottom with the captured image.
2. **PDF Report:** Generate a report and verify (if possible via file reading) that it contains multiple lines or at least handles the data correctly.
3. **ZoomEye:** Verify that the "Insufficient credits" error message is displayed correctly and that the points are refreshed.
