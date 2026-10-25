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
