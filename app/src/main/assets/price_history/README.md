# Bundled price history datasets

Place the historical daily price JSON files used by CashTrack in this directory:

- `pi_usdt_daily.json`
- `shibch_daily.json`
- `furu_daily.json`
- `paryonusd_daily.json`
- `moriausd_daily.json`

Keep each dataset's source, retrieval date, price units, and timestamp convention documented. CashToken history from the Cauldron API is quoted in BCH satoshis; Pi history from an exchange is quoted in USDT. Missing trading days should remain missing rather than being filled with zero prices.

The JSON datasets have not been added yet; this directory is a placeholder for them.