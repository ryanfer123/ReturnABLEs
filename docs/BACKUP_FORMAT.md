# Backup and evidence formats

Both formats are unencrypted ZIP files. Purchase dates use ISO `YYYY-MM-DD`;
amounts use integer minor units with an ISO currency code. For example,
`amountMinor: 12345, currency: "INR"` means INR 123.45. The original receipt bytes
are retained and named by their full lowercase SHA-256 fingerprint plus an allowed
extension (`jpg`, `png`, `webp`, or `pdf`). Filenames never derive from product titles.

## Restorable backup, schema version 1

```text
manifest.json
receipts/<64-character-sha256>.pdf
receipts/<64-character-sha256>.jpg
```

`manifest.json` is a UTF-8 JSON object:

```json
{
  "schemaVersion": 1,
  "purchases": [
    {
      "id": "example-id",
      "title": "Headphones",
      "merchant": "Corner shop",
      "purchasedOn": "2024-02-29",
      "amountMinor": 12345,
      "currency": "INR",
      "returnBy": "2024-03-30",
      "warrantyUntil": null,
      "notes": "",
      "receiptName": null,
      "receiptMime": null,
      "ocrText": "",
      "status": "ACTIVE",
      "createdAt": 1709164800000,
      "updatedAt": 1709164800000
    }
  ]
}
```

Each field shown is required; optional values use explicit `null`. Status is one
of `ACTIVE`, `RETURNED`, `CLAIMED`, or `ARCHIVED`. Receipt name and MIME type must
both be present or both be null. The MIME type must match the filename extension.
Different purchases can reference one receipt, which appears once in the archive.

Validation rejects unsupported versions, malformed records, duplicate purchase IDs,
duplicate ZIP entries, directory entries, traversal/absolute paths, unexpected
files, missing or unreferenced receipts, and fingerprint mismatches. It also
checks purchase dates, amount precision/types, field lengths, and timestamps.
Unknown JSON metadata fields are ignored; required field types are checked.

Uncompressed limits apply while streaming, regardless of ZIP entry size claims:

| Limit | Maximum |
| --- | --- |
| Purchases | 1,000 |
| ZIP entries | 2,001 |
| Manifest | 5 MiB |
| Each receipt | 20 MiB, nonempty |
| Aggregate contents | 100 MiB |

Inspection requires an empty private staging directory. On failure it removes
files created during that inspection. Only after all records and receipt files
validate does the caller commit them. The app's restore adds missing IDs and
preserves purchases already on the device. It does not merge two records with the
same ID. Backups do not contain Android permission state or reminder delivery logs.

## Evidence folder, schema version 1

```text
README.txt
purchase.json
receipt-text.txt             # only when OCR text exists
receipt/<64-character-sha256>.pdf  # only when attached
```

`README.txt` lists the purchase, merchant, dates, amount, status, notes, receipt
fingerprint and interpretation limits. `purchase.json` has this outer structure:

```json
{
  "schemaVersion": 1,
  "format": "returnables-evidence",
  "purchase": { "...": "the complete purchase record described above" }
}
```

The receipt and OCR text are evidence for someone to review. OCR can be wrong;
deadlines are user-confirmed entries rather than independently verified merchant
policies. Export does not submit a claim or establish eligibility. An evidence
folder is not a restorable backup and intentionally uses a separate layout.
