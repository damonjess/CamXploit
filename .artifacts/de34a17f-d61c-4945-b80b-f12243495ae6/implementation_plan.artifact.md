# Fix Bottom Navigation Obscured by System Bars

The app's bottom navigation tabs are being obscured by the system navigation bar (back/home/recent buttons). This is because the app targets Android 15 (API 35), where edge-to-edge display is enforced by default, but the bottom navigation bar doesn't account for the system insets.

## User Review Required

> [!NOTE]
> I will be enabling explicit edge-to-edge support in the `MainActivity` to ensure the system bars are correctly handled across different Android versions and device types.

## Proposed Changes

### [UI Layout]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- **Enable Edge-to-Edge**: Call `enableEdgeToEdge()` in `MainActivity.onCreate`.
- **Apply Insets to Bottom Bar**: Wrap the `ScrollableTabRow` in the `Scaffold`'s `bottomBar` with a container that applies `navigationBarsPadding()`. This will push the tabs up so they are above the system buttons while maintaining the background color.
- **Ensure Top Bar Insets**: The current `Scaffold` usage with the `padding` parameter should already handle the status bar, but I will verify if any further adjustments are needed for the header.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure no compilation errors.

### Manual Verification
- Deploy to a device with a gesture or button-based navigation bar.
- Confirm that the "LAN", "STORM", and other tab labels are fully visible and not overlapping with system buttons.
- Confirm the status bar area (top) still looks correct.
