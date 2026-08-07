# Expense Tracker

Expense Tracker is a native, offline Android app that totals card spending since your last payday. It reads only notifications posted by the configured CommBank package, extracts completed purchases locally, and keeps all data in a Room database on the device.

## Features

- CommBank-only `NotificationListenerService` with a configurable package name
- Multiple testable purchase-message patterns and SHA-256 duplicate detection
- Current-period total and newest-first transaction list
- Payday reset boundaries that preserve prior transactions
- Manual transactions with an editable date and time
- Parser preview, duplicate-safe CSV export/import, and local-data deletion
- Bill splits with optional participant names, paid/unpaid status, exact-payment matching, and manual assignment
- Confirmed high-resolution bill-split image previews with native Save and Share actions
- Copyable payment-text previews with clipboard and native Messenger-compatible sharing
- Separate Spending and Earnings tabs; incoming payments that do not match an active split count as earnings
- All History tab for transactions from prior reset periods and CSV imports
- Multi-select History actions for permanent deletion and combined Sum & Split
- Press-and-hold selection in Spending and History (no separate selection button)
- Recycle Bin for restoring deleted transactions, deleting individual items forever, or emptying the bin after confirmation
- Split counts always include you; repayments reduce the dashboard to your net personal spending
- Unnamed splits auto-assign exact repayments in arrival order; named splits retain uncertain payments for manual assignment
- Partially paid splits can be closed with the unpaid shares covered by you, then reopened for any chosen unpaid people while completed payments stay locked
- Sum & Split replaces selected rows with one titled combined transaction while preserving its original items internally
- Android Back exits selection/actions first, returns other tabs to Spending, then exits from Spending
- Locally saved default account name and PayID prefill future splits
- Consistent light-blue Material 3 theme
- Independent Reset Spending, Reset Income, and Reset Both period controls
- Kotlin, Jetpack Compose Material 3, Room, Flow, MVVM, repository pattern, and Hilt
- No internet permission, analytics, banking credentials, SMS access, or accessibility service

## Open in Android Studio

1. Install a recent Android Studio with Android SDK 36 and JDK 17 or newer.
2. Choose **Open** and select this repository's root folder.
3. Allow Gradle sync to finish. The included wrapper uses Gradle 8.13.

## Build and install

In Android Studio, select an Android 8.0 (API 26) or newer device and click **Run**. From a terminal you can also run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with Android Studio or `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Enable notification access

1. Launch Expense Tracker.
2. Read the privacy explanation and tap **Open notification access settings**.
3. Enable **Expense Tracker notification access** and accept Android's warning.
4. Return to the app and confirm the status says enabled.
5. In the CommBank app/Android notification settings, ensure transaction notifications are enabled.

Android—not this app—controls notification-listener permission. Expense Tracker checks the posting app's exact package name before reading a notification and never stores the original notification text.

## Confirm the CommBank package name

The default is `com.commbank.netbank`. Package identifiers can change by version or region, so verify the installed official app before relying on automatic capture:

1. Open **Expense Tracker → Settings**.
2. Compare the configured value with the package reported by Android/Play Store or by running `adb shell pm list packages | findstr commbank`.
3. Enter the exact official CommBank package and tap **Save settings**.

Only an exact match is processed. Do not enter a wildcard or the package of an unofficial app.

## Test the parser

1. Open **Settings → Test notification parser**.
2. Paste a sample such as `You spent $12.50 at WOOLWORTHS`.
3. Tap **Preview result** and confirm the merchant and AUD amount.

Messages mentioning declined, reversed, refunded, pending, or cash-withdrawal activity are intentionally ignored. If a legitimate message format is not recognised, use **Add Transaction** on the home screen and add a parser unit test before extending `TransactionParser`.

## Reset on payday

On payday, tap the large **Reset Spending** button and confirm. The current period receives an end timestamp and a new period starts immediately. Previous transactions remain in Room for CSV export and future history features; they no longer count toward the dashboard total.

## Privacy and storage

The manifest deliberately omits `INTERNET`, SMS, and accessibility permissions and disables Android backup. Stored transaction fields are merchant, integer amount in cents, timestamp, source, creation time, and duplicate hash. Bill splits additionally store names and statuses, parsed incoming sender names, and the PayID/account name explicitly entered for sharing. Raw notification text is never stored. The settings screen can export all transactions to a user-selected CSV file or permanently delete the local database contents.

## Tests

Local unit tests cover currency parsing, all requested notification examples, commas/apostrophes/additional text, ignored activity, stable duplicate IDs, integer spending totals, and reset-period boundaries.
