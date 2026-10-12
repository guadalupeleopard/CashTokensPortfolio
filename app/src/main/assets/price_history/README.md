# Bundled CashToken price history

CashTrack bundles daily historical price datasets in this directory so the supported CashToken charts can display historical data without downloading the entire history at runtime.

## Datasets

- `shibch_daily.json` — SHIBCH
- `furu_daily.json` — FURU
- `paryonusd_daily.json` — ParyonUSD
- `moriausd_daily.json` — Moria USD

Each file contains a `history` array with `time` (Unix timestamp in seconds) and `avg`, `min`, and `max` prices quoted in BCH satoshis. CashTrack plots the `avg` value and converts timestamps to milliseconds for the chart.

The app selects these bundled files by the asset's display name. CashTokens without a matching bundled dataset continue to use the live Cauldron indexer history endpoint when available.

When updating a dataset, preserve its JSON structure, document the source and retrieval date, avoid duplicate timestamps, and leave missing trading days missing rather than filling them with zero prices. Historical data can contain outliers; review suspicious values before publishing updates.
