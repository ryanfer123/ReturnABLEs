# Receipt workflows and design decisions

Reviewed 27 October 2026. These are product-design references, not claims about
competitors' implementation quality or policy accuracy.

- [Receipt & Warranty Vault](https://rw-vault-app.web.app/index.html) groups
  search, warranty-status filtering, expiry summaries and portable exports.
  returnABLEs adds composable filters and receipt-text search so a saved item can
  be found by its merchant, notes or an extracted serial reference.
- [Fyld](https://www.fyldapp.com/) highlights deadline urgency and keeping proof
  attached to the purchase. returnABLEs keeps original files and requires explicit
  confirmation of extracted dates rather than guessing merchant terms.
- [Compose animation guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)
  documents AnimatedContent for transitions between UI states. Navigation uses
  short, finite native transitions; controls remain usable after animation scales
  are disabled in Android settings.
- [Android common intents](https://developer.android.com/guide/components/intents-common#Calendar)
  documents opening a calendar event editor. Calendar export opens a draft for
  the user to review and save; returnABLEs needs no calendar read/write permission.

## Implemented scope

The ledger has All, Upcoming, Overdue and Closed views; receipt-only filtering;
search across item, merchant, notes and extracted receipt text; and deadline,
newest and title sorting. Summary counts count each active purchase once. A
purchase with an expired return date and a future warranty can appear in both
Upcoming and Overdue: each describes a different recorded deadline. Closed items
appear in neither deadline view.

Calendar drafts are all-day events with UTC date boundaries. Drafts include only
the item title, merchant and a reminder to check original terms; receipt contents
and private notes are omitted. Events are not saved automatically and no return
or claim is submitted. Repeated exports can create duplicate events if the user
saves each draft; there is no calendar synchronization.

## Visual direction

Material 3 provides native controls and accessibility behavior. Paper backgrounds,
ink outlines, cobalt actions and lime attention panels borrow restraint from
neobrutalist print design. Dark mode follows the system. Date urgency is always
written in text, not conveyed through colour alone. No decorative network assets
or web animation runtime are needed.
