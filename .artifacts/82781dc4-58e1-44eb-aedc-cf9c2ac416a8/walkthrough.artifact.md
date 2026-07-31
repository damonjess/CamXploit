# Walkthrough - Insecam WebView Browser & Direct Stream Integration

I have replaced the broken Insecam scraper with a robust WebView-based browser and added a Direct Stream panel for manual RTSP/HTTP playback. This solves the Cloudflare blocking issue and provides a unified workflow for OSINT discoveries.

## Changes Made

### 1. **Insecam Native Browser**
- **[NEW] [InsecamBrowserScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/InsecamBrowserScreen.kt):** A dedicated WebView wrapper that:
    - Loads `insecam.org` natively, bypassing Cloudflare scraping issues.
    - Intercepts camera page links (`/en/view/ID/`) and automatically launches them in our high-performance `StreamActivity` viewer.
    - Provides a "Reload" function and a clean dark-themed header.

### 2. **Manual Stream Control**
- **[NEW] [DirectStreamPanel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/DirectStreamPanel.kt):** A new utility panel in the INTEL section that:
    - Allows users to paste any RTSP or HTTP stream URL directly.
    - Supports custom labeling for streams.
    - **Common Paths:** Includes one-tap presets for default Hikvision, Dahua, Axis, and XMEye RTSP paths, making it easy to test exposed targets found via ZoomEye or LAN scans.

### 3. **OSINT Logic Cleanup**
- **[MODIFY] [OsintScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/OsintScreen.kt):** Replaced the country/camera list with the new browser and direct stream UI.
- **[MODIFY] [OsintViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/osint/OsintViewModel.kt):** Removed legacy scraper state (`_countries`, `_cameras`, etc.) and associated methods, reducing memory overhead and technical debt.
- **[DELETE] `InsecamClient.kt`:** Removed the obsolete regex-based scraper.

## Verification Results
- **Build:** The project compiles successfully.
- **Compatibility:** Added `@androidx.media3.common.util.UnstableApi` to ensure compliance with the Media3 library used by `StreamActivity`.
- **Workflow:** Verified that `GlobalOsintSheet` correctly uses the new public `PublicCamsPanel`.

> [!TIP]
> When you find a camera IP on ZoomEye that has port 554 open, you can now quickly test it by copying the IP into the **Direct Stream** panel and selecting one of the **COMMON PATHS** presets.
