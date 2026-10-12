# CashTokens Portfolio — Android source v1.2

Native Android Java application (Android 8+). Includes a GitHub Actions workflow to build a debug APK in the cloud.

## Build an APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Wait for **Build CashTokens Portfolio APK** to finish successfully.
3. Open the completed run and download the `CashTokensPortfolio-debug-apk` artifact. Unzip it to get the `.apk` file.
4. Transfer the APK to your Android phone and install it. Android may ask you to allow installs from the app you used to download/open the file. Only install APKs you built or trust.

The workflow uses Java 17, Android SDK 35, and Gradle 8.9, then runs `assembleDebug` and uploads the APK as a workflow artifact. It produces a **debug APK**, not a Play Store release package.

## Market feeds
- CoinGecko public simple/price endpoint for BCH, PI and USDT.
- TokenStork public directory endpoint for CashTokens, searched by full 64-character category ID.
- USD prices are accepted only from explicitly named USD fields; alternatively satoshi-denominated prices are converted using BCH/USD. Ambiguous prices are never guessed.

**Important:** TokenStork response field mapping and live CashToken prices have NOT been verified against a successful API response. Some or all CashTokens may show `Price unavailable`. API availability, throttling and terms can change.

## Privacy
No seed phrase, wallet keys, or account required. Holdings are saved locally. Network requests expose the category IDs being queried to market-data providers.
