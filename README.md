# DocVault — Personal Android Document Manager

**Configuration:** Personal use · Manual Google Drive backup/restore · Biometric unlock (fingerprint fingerprint required).

## Included in this source project
- Capture full-resolution document photos using the Android camera app.
- Save privately under `documents/YYYY/Month/Category/`.
- Categories: Aadhaar, PAN, Bank, Education, Medical, Bills, Other.
- Browse recent scans; open an item to create PDF, share, or delete.
- Select multiple captured pages and create an A4 PDF.
- Open/share the PDF with a compatible Android PDF app; use that app's Print action or Android print service.
- Manual ZIP backup through Android's Storage Access Framework. Choose Google Drive in the file picker to save the ZIP.
- Restore a ZIP selected from Google Drive. Restore merges files into the local archive.
- Strong-biometric fingerprint prompt at app launch; no bypass is offered if fingerprint enrollment is missing.

## Build on Windows
1. Install **Android Studio** (current stable) and Android SDK Platform 35.
2. Extract this folder to a writable path, for example `C:\Projects\DocVault`.
3. Open the `DocVaultAndroid` folder in Android Studio and allow Gradle sync.
4. If prompted, install the Android Gradle Plugin / SDK components.
5. Connect an Android phone with USB debugging enabled, or create an emulator.
6. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
7. The debug APK is generated at `app\build\outputs\apk\debug\app-debug.apk`.

Command line (after Android Studio has installed SDK and Gradle wrapper has been generated):
`gradle assembleDebug` (or run `BUILD_APK.bat` after installing Gradle and configuring the Android SDK).

## Google Drive manual backup
- Tap **Create manual backup**.
- In the Android save dialog, choose Google Drive (if installed and signed in), select a folder, and save the ZIP.
- To recover on a new installation, tap **Restore backup**, browse to the ZIP in Drive, and select it.
- Keep a second copy of important documents. Do not uninstall before verifying a recent backup.

## Important limitations of this initial build
- This is a starter implementation, not a production-audited release. It has not been compiled or device-tested in this environment.
- Camera capture uses the phone's camera app; automatic edge detection, perspective correction, OCR, and automatic document classification are not included yet.
- The PDF is A4 and contains selected captured images. For printing, open it in a PDF viewer and choose Print; printer availability depends on Android Print Service / manufacturer plugin / Wi-Fi printer.
- Backup is deliberately manual. The ZIP is not encrypted by this version; store it in a protected Google Drive account and avoid sharing it publicly. Add client-side encryption before storing highly sensitive identity documents in cloud storage.
- Fingerprint prompt protects app entry, but Android app-private storage and OS device security remain part of the trust boundary. If the device has no enrolled strong fingerprint, the app blocks access until one is enrolled. No app can guarantee recovery if the backup is missing or corrupt.
- Google Drive is accessed through Android's document picker; the app does not request broad Drive account access or store Google credentials.
