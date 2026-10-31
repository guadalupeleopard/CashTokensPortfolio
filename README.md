# CashTrack

CashTrack is a cryptocurrency tracker for Bitcoin Cash (BCH), its CashTokens DeFi ecosystem, and other crypto assets.

## Disclaimer

This software is provided **as is**, without warranties of any kind. It may contain bugs, display inaccurate or unavailable market data, or stop working when third-party services change. Use it at your own risk. Always verify important information independently.

## Source code and usage

The source code is publicly available for viewing. **No license is granted to copy, modify, redistribute, or otherwise reuse this software. All rights are reserved by the copyright holder, except where applicable law provides otherwise.**

## Build an APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Wait for the **Build CashTokens Portfolio APK** workflow to finish successfully.
3. Open the completed run and download the `CashTokensPortfolio-debug-apk` artifact. Unzip it to get the `.apk` file.
4. Transfer the APK to your Android phone and install it. Android may ask you to allow installs from the app you used to download/open the file. Only install APKs you built or trust.

The workflow uses Java 17, Android SDK 35, and Gradle 8.9, then runs `assembleDebug` and uploads the APK as a workflow artifact. It produces a **debug APK**, not a Play Store release package.

## Market data

- CoinGecko public simple/price endpoint for BCH, PI, and USDT.
- CauldronSwap (Riften Labs indexer) current-price endpoint for CashTokens, queried by full 64-character category ID. Token decimals are retrieved from the indexer's BCMR token metadata endpoint.
- Price availability and accuracy depend on third-party services.

**Important:** CashToken prices depend on successful responses from the CauldronSwap current-price and BCMR metadata endpoints, as well as the BCH/USD conversion rate. Some or all CashTokens may show “Price unavailable” when those services are unavailable or return incomplete data. API availability, throttling, and terms can change.

## Privacy

No seed phrase, wallet keys, or account are required. Holdings are saved locally. Network requests expose the category IDs being queried to market-data providers.
