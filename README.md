# returnABLEs

A native Android purchase notebook for keeping receipts, checking return and
warranty dates, and assembling evidence when something needs to go back.

## What it does

- Add purchases with exact amounts and user-confirmed return/warranty dates.
- Attach a JPEG, PNG, WebP or PDF receipt, or capture a receipt image.
- Extract Latin-script text from images on device and review proposed merchant,
  dates and totals. Ambiguous numeric dates remain alternatives; no return policy
  is inferred. PDFs can be attached and viewed; their details are entered manually.
- Search purchases, inspect deadlines, edit details, and mark purchases returned,
  claimed or archived. Closed purchases stop producing deadline reminders.
- Request local notifications for active deadlines in the next three days.
- Share a readable evidence ZIP with structured details and the original receipt.
- Export or restore a versioned backup of purchases and receipt files.

The app uses Kotlin, Jetpack Compose, SQLite, bundled ML Kit text recognition and
WorkManager. It requires Android 8.0 (API 26) or newer. No account or backend is
required.

## Build and install

Use JDK 17 and an Android SDK with platform 35 installed. Set `ANDROID_HOME` or
create a local, untracked `local.properties` containing `sdk.dir=/your/android/sdk`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can
also open the repository and run the `app` configuration. The repository's Android
workflow runs unit tests, lint and a debug build, then uploads the APK artifact.

## Use

Add a purchase, attach its receipt if available, and confirm the extracted details.
Enter the exact dates from the merchant or warranty terms. A deadline remains
inclusive on its final day. Open a saved purchase to update its status, view the
receipt, or share an evidence folder. Settings provides backup and restore.

Notifications need permission on Android 13 and later. Background checks run
approximately every 12 hours; Android battery settings and scheduling can delay
or suppress delivery. The purchase list remains the place to check deadlines.
Recording a returned or claimed status does not contact the merchant or file a claim.

## Privacy and exports

Purchase records and attached receipts stay in app-private local storage. OCR runs
on device. Sharing and document export create copies in the destination you choose.
The app does not send receipts to an OCR service or include analytics.

Backup and evidence ZIPs are **not encrypted** and can contain addresses, payment
references, notes and full receipt text. Protect those files. Uninstalling the app
can remove local data, so export a backup you control before uninstalling.

A backup is for restoration; an evidence folder is for reading and sharing.
Restore validates records, bounded ZIP contents and receipt fingerprints before
adding missing purchase IDs. Existing IDs on the device are preserved.
See [backup and evidence formats](docs/BACKUP_FORMAT.md).

## Verification

Pure JVM tests cover money precision, leap days, inclusive/expired deadlines,
closed purchases, ambiguous receipt dates, JSON roundtrips and bounded archive
validation. Build results and device/emulator observations should be recorded
separately; a passing unit test does not establish notification delivery or OCR
quality on a particular receipt.

See [implementation plan](PLAN.md) and [design notes](docs/DESIGN.md).

## License

[MIT](LICENSE), copyright 2026 Ryan Fernandes. Dependencies retain their own licenses.
