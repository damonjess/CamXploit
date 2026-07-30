# Implementation Plan - Offline Tools Tab

Add a new "TOOLS" tab to the application containing offline utilities: Hash Identifier, Decoder (Base64, Hex, JWT), Password Generator, and String Analyzer.

## User Review Required

> [!NOTE]
> I am inserting the "TOOLS" tab between "SAVED" and "SENTINEL" in the bottom navigation. This shifts the "SENTINEL" tab to index 8. I will update all internal references to the Sentinel tab to ensure navigation remains consistent.

## Proposed Changes

### Tools Module

#### [NEW] [CryptoUtils.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/tools/CryptoUtils.kt)
- Create a new utility object for offline cryptographic and decoding operations.
- Implement `identifyHash`, `decodeBase64`, `decodeHex`, `decodeJwt`, `generatePassword`, and `estimateEntropy`.
- Include the optional `analyzeString` function.

### UI Components

#### [NEW] [ToolsScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/ToolsScreen.kt)
- Create the Compose UI for the Tools screen.
- Implement sections for Hash Identification, Decoding, Password Generation, and String Analysis.
- Maintain the app's dark aesthetic with neon green and cyan accents.

### Main Integration

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Add `import com.spyboy.camxploit.ui.ToolsScreen`.
- Update the `tabs` list in `CamGuardianApp` to include the "TOOLS" tab with `Icons.Default.Build`.
- Update the `when (selectedTab)` block to route index 7 to `ToolsScreen()` and shift `SentinelScreen` to index 8.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors or missing dependencies: `gradlew assembleDebug`.

### Manual Verification
- Deploy the app and navigate to the new "TOOLS" tab.
- Verify Hash Identifier with a known MD5/SHA-1 hash.
- Verify Decoder with Base64 and Hex strings.
- Verify Password Generator generates strings and calculates entropy.
- Verify the "SENTINEL" tab still works correctly at its new position.
