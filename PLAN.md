# returnABLEs implementation plan

Native Android app, local first. The first release helps someone keep a receipt, confirm purchase details, track return and warranty deadlines, receive local reminders, and share purchase evidence.

## Core workflow

1. Add a product manually or attach/capture a receipt image. On-device OCR proposes merchant/date/amount with original text available for review. Nothing infers a merchant return policy.
2. Confirm purchase date and optional exact return/warranty dates. One receipt may be attached to multiple product records, each with independent deadlines.
3. See upcoming deadlines, search purchases, reopen receipts, edit details, mark a product returned/claimed, and export its evidence.
4. Enable Android notifications explicitly. Persistent background work checks upcoming deadlines; delivery is approximate and subject to Android settings.
5. Export and restore a versioned backup including receipt files. Import validates paths, sizes, dates and records before committing.

## Architecture

Kotlin and Jetpack Compose; single Android app with separate domain, data, reminder and UI packages. Domain rules are pure Kotlin. SQLite stores records; receipt bytes are copied to app-private storage. No account, analytics or backend is required. Bundled ML Kit handles Latin-script receipt images on device. FileProvider and system document pickers control sharing/import.

## Design

Use frontend-design and the high-level art-direction principles of build-awwwards-quality-sites, adapted to native interaction rather than importing web motion libraries. A useful personal purchase notebook: clear deadlines, original receipt context, restrained motion, accessible controls, empty/error/loading states and Android font scaling. Write the detailed token/layout direction before UI implementation.

## Verification and publication

Pure domain tests cover leap days, inclusive deadlines, ambiguous OCR dates, money and closed purchases. Android build/lint and emulator tests cover add/edit/search, receipt attachment/OCR confirmation, reminders, evidence export, backup restore and process restart. Publish tested milestones to GitHub; record unverified behavior explicitly. Deliver an installable debug APK with checksum when build tools permit.
