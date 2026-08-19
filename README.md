# Complete Intel tab upgrade bundle

This bundle combines the earlier pagination and MJPEG playback fixes with the Intel-tab reliability and usability upgrades.

## Included upgrades

| Area | Upgrade delivered |
|---|---|
| Source reliability | Country, GitHub, and Opentopia loading errors now propagate to the view model instead of silently becoming empty lists. |
| Source health | The Intel screen maintains source-specific loading, healthy, partial, and error states and displays them below search. |
| Stable identities | GitHub and Opentopia cards derive deterministic IDs from their canonical URLs, improving refresh deduplication and diagnostics. |
| Feed verification | Each public-result card has **CHECK** and **INFO** actions. The check identifies MJPEG, snapshot, RTSP, web, or unavailable responses and records diagnostics. |
| Filters and sorting | Source grids support All, Verified, MJPEG, and Snapshot filters plus an A–Z toggle. |
| Diagnostics | A camera diagnostics sheet exposes source, verification result, response content type, effective URL, and check result. |
| Pagination | Country pagination checks for the directory’s actual next-page controls rather than assuming every page has six cards. The grid shows its current page. |
| Source transparency | Country-directory counts are labelled as estimates that may vary. |
| User cameras | Intel now includes a **MY CAMS** tab that reads the existing saved-camera Room database and presents the same filter, verification, diagnostic, play, and save controls. |
| Navigation | GitHub is treated as selected under Public Cams; My Cameras has its own tab index. |
| MJPEG smoothness | The viewer waits for HTTP classification, keeps an effective MJPEG protocol, and uses one continuous renderer connection instead of competing display/background MJPEG connections. |

## Files in the bundle

```text
app/src/main/java/com/spyboy/camxploit/
├── StreamSource.kt
├── StreamViewerActivity.kt
├── StreamViewModel.kt
├── osint/
│   ├── GitHubMotionJpegClient.kt
│   ├── InsecamScraper.kt
│   ├── IntelModels.kt                 (new)
│   ├── OpentopiaScraper.kt
│   └── OsintViewModel.kt
└── ui/
    ├── FastMjpegPlayer.kt
    ├── OsintScreen.kt
    └── PublicCamsPanel.kt
```

## Install

Copy the `app` folder from this archive into the root of your Android Studio project, choosing **Replace** when prompted. Then run **Sync Project with Gradle Files**, build the debug variant, and install the app on a device.

## Test checklist

| Test | Expected result |
|---|---|
| Open **INTEL → PUBLIC CAMS** | The existing country directory is displayed and its count label states that estimates may vary. |
| Select a country and load pages | The header displays the current page; **LOAD 6 MORE CAMERAS** appears only when the source reports another page. |
| Force an unavailable network/source | A red source error banner states the actual failure rather than showing a blank result list. |
| Open Opentopia or GitHub results | The matching Public Cams tab remains selected and a source-health banner appears. |
| Select **CHECK** on a camera | The card updates to MJPEG, Snapshot, RTSP, Web page, or Unavailable when the probe completes. |
| Select **INFO** | The diagnostics dialog displays effective URL, response content type, verification state, and message. |
| Select **MY CAMS** | Your Room-saved cameras appear in an Intel collection with filters and diagnostics. |
| View an owned MJPEG source | It uses continuous MJPEG playback rather than the periodic snapshot refresh path; normal playback should have one stream connection. |
| Validation limitation | The source-level cross-file references were verified after modification. A full Gradle compilation could not be run in the sandbox because it has no Android SDK or Kotlin compiler configured. The project must be synced and built in your local Android Studio environment. |

## Notes

Third-party directories can change their HTML or block automated requests at any time. The new source-health banner is intended to make that visible. Keep cameras you own or are authorized to access in **MY CAMS**; use the verification status and diagnostics dialog to troubleshoot them.
