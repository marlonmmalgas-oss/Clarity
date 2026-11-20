# CLARITY — Android project

A native Android personal-finance tracker with a dark fintech interface and local SQLite persistence.

## Included
- Overview with balance, income/expense metrics, monthly cash-flow line chart and expense-category doughnut chart.
- Income and expense transactions, categories, notes, dates and delete action.
- Monthly estimated income, expenses and savings plan alongside actual transaction totals.
- Custom expense categories.
- Savings goals with progress tracking and contributions.
- Local Room/SQLite database. Changes are saved immediately and remain after normal app closes/restarts.
- GitHub Actions workflow that builds a debug APK automatically.

## Build the APK in GitHub
1. Extract this ZIP.
2. Upload the **contents** of the extracted `CLARITY_Android` folder to the root of your own GitHub repository (not the enclosing folder).
3. Open the repository's **Actions** tab. Run **Build CLARITY APK** if it has not run automatically.
4. Open the successful workflow run, scroll to **Artifacts**, and download `CLARITY-debug-apk`. The APK inside is `app-debug.apk`.
5. Install the APK on your Android device. If Android asks, allow installation from that source.

## Open in Android Studio
Open this folder as a project and allow Gradle sync. Select the `app` run configuration and run on a device/emulator.

## Data and backup warning
The app stores its budget data on the device in `clarity_local.db`. It persists through normal app restarts. This first release does not yet include an in-app export/import backup function or cloud sync. Back up the device; uninstalling the app or clearing its storage can delete the local database.

## Technical
- Kotlin, Jetpack Compose, Material 3
- Room database (SQLite)
- minSdk 26 / compileSdk 35
- Package: `za.co.clarity`
