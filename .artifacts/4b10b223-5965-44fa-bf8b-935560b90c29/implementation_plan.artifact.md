# Implementation Plan - OSINT Final Refinements

The goal is to resolve the credit parsing mismatch for ZoomEye and improve the success rate of the Public Cams scraper by implementing session warming.

## Proposed Changes

### [ZoomEye Accuracy]

#### [MODIFY] [ZoomEyeClient.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/ZoomEyeClient.kt)
- **Point Mapping**: Explicitly map "General Points" from `data.subscription.points` as reported in the ZoomEye v2 API spec. This will resolve the 0 credit display when points are actually available.
- **Error Transparency**: Include the API response code in the error message to help differentiate between "Out of Credits" (402) and other policy blocks.

### [Public Cams / Insecam Stability]

#### [MODIFY] [InsecamClient.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/InsecamClient.kt)
- **Session Warming**: Implement a "warm-up" request to the root Insecam URL before fetching countries. This helps establish necessary Cloudflare/Session cookies in the `CookieJar`.
- **User-Agent Update**: Use a more specific "Android 14 Chrome" agent to better match the app's traffic profile.

### [UI Enhancement]

#### [MODIFY] [OsintScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/OsintScreen.kt)
- **Credit Labeling**: Rename the credit display to "GENERAL POINTS" to match the ZoomEye dashboard nomenclature.
- **Loading Polish**: Ensure the loading state is clearly visible during "Session Warming".

## Verification Plan

### Automated Tests
- Build `app:assembleDebug`.

### Manual Verification
- **ZoomEye**: Enter API key, verify that "GENERAL POINTS" shows approximately 2,980.
- **Public Cams**: Verify that the "Session Warming" allows the country list to load without the "No countries found" error.
