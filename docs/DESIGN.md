# returnABLEs interface direction

A native receipt ledger: confident cobalt (#2347DA), paper white (#FFFFFF),
lavender page (#F2F2FF), ink (#17234A), slate (#59617A), and warning red (#A52C35).
System sans typography keeps Android font scaling and language support; display
headings use strong weight and generous spacing. Deadline dates are the visual
anchor; merchant and receipt details form quiet ledger rows beneath them.

Home: brand and settings / actionable introduction / search / Upcoming, All,
Closed filters / date-ordered entries / add purchase. Details presents the
original receipt and user-confirmed dates before status actions. Forms retain
explicit control: OCR candidates are suggestions selected with buttons, never
silently accepted dates or invented return policies. Settings explains reminders,
local storage, evidence sharing and plaintext backup risks.

Awwwards quality and frontend-design inform original hierarchy and restraint,
adapted to native Material 3. No web animation libraries, smooth scrolling engine,
Three.js, external imagery, customer proof or decorative receipt illustrations.
Native scrolling and interactions follow system motion settings. All tappable
controls target at least 48dp. Tablet width is bounded; compact screens scroll
without clipping forms. Core icons use the licensed Android Material collection.

Checked against Android's official Compose date-picker and accessibility guides:
https://developer.android.com/develop/ui/compose/components/datepickers
https://developer.android.com/develop/ui/compose/accessibility/api-defaults

Compile and rendered emulator validation are coordinated by the root agent.


## Editor continuity

The edit seed is saved as Bundle-safe primitive strings, preserving purchase ID,
status, timestamps and receipt reference across recreation. Individual text fields
use saveable state. Receipt draft attachment/OCR state remains the supplied
application state, owned by the activity/view model. Header Back, system Back,
and Cancel editing request confirmation before dropping unsaved changes.

Compose regression source covers explicit OCR date selection, invalid input,
status confirmation, search/filters, restoration of an edited identity and reused
receipt, and keeping edits after Back. Build/emulator results are recorded by the
coordinating agent; test source alone is not a rendered validation claim.

## Material ledger refresh

The new direction combines Material 3 behavior with bold, receipt-like framing:
warm paper #FAF8F0, ink #172034, cobalt #2447DA, lime #D8FF70 and white #FFFFFF.
Dark mode uses navy paper #131923 and light ink #EBEEF7, preserving the same
hierarchy. Solid outlines and small corner radii organize the ledger; a cobalt
intro and a lime urgency strip carry emphasis. No decorative fake receipt art.

Useful controls lead the composition: real item counts for overdue and due-soon,
Overdue/Upcoming/All/Closed filters, receipts-only selection, and date/title/newest
sorting. No amount ranking compares different currencies. Detail screens offer
calendar drafts for recorded dates, with external user review. Errors announce
through a polite live region. Auto-mirrored back navigation supports RTL.

Route changes use a short native crossfade and small vertical entrance. Native
Compose animation duration scaling respects the Android system motion setting.
Content-size animation is limited to supporting panels; no scrolling engine,
continuous effect, external asset or 3D runtime is added.
