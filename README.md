# Hisab (হিসাব) – Personal Expense Tracker for Android

Offline personal finance app (Kotlin, Jetpack Compose, Room). Tracks cash, bKash, Nagad, Rocket, bank and custom accounts, income, expenses, transfers, borrowed money, lent money and repayments. English and Bengali, BDT (৳) with lakh/crore grouping, light and dark mode.

## How the numbers work

There is one ledger table (`transactions`). **Balances are never stored, they are always calculated** from the opening balance plus every ledger row, so edit and delete can never leave a wrong balance behind.

| Entry | Account balance | Debt | Receivable | Income / Expense reports |
|---|---|---|---|---|
| Income | + | | | income |
| Expense | − | | | expense |
| Transfer | − source, + destination (total unchanged) | | | not counted |
| Borrowed money received | + | + | | not counted |
| Repay borrowed money | − | − | | not counted |
| Money lent | − | | + | not counted |
| Repayment received | + | | − | not counted |

* Money is stored as whole poisha (`Long`, 1 ৳ = 100 poisha). Input is parsed with `BigDecimal`. No `Double` is used for balances.
* Every save, edit, delete and restore is validated against the whole ledger inside one database transaction (`Ledger.validate`). A change that would break the books (repaying more than owed, transferring to the same account, a repayment without a loan) is rejected and nothing is written.
* Reports use weeks that start on Saturday.

## Features

Dashboard (total, cash / wallet / bank, debt, receivable, today, this month, 7-day chart, accounts, recent), quick-add buttons, history with search / date range / account / type filters, edit and delete, loans with progress and due dates, daily / weekly / monthly / yearly reports with bar and pie charts, backup and restore (JSON), CSV export (opens in Excel), optional PIN lock, optional screenshot blocking, no internet and no permissions.

## Project layout

```
settings.gradle.kts, build.gradle.kts, gradle.properties
.github/workflows/build.yml        cloud build: tests + APK
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/...               theme, launcher icon
app/src/main/java/com/hisab/tracker/
  Ledger.kt        pure logic: money, balances, validation, reports (unit tested)
  Data.kt          Room entities, DAO, validated repository, JSON backup, CSV
  AppViewModel.kt  state, settings, PIN
  Strings.kt       English + Bengali texts
  Ui.kt            theme, components, charts, pickers
  Screens.kt       dashboard, history, loans, reports, settings
  Forms.kt         transaction, account and loan-detail forms
  MainActivity.kt  entry point, bottom navigation, file pickers, PIN screen
app/src/test/java/com/hisab/tracker/LedgerTest.kt
```

## Build without a computer (GitHub Actions)

1. Create all files in a new GitHub repository (on the phone use **Add file → Create new file**; typing `/` in the name creates folders). Create `.github/workflows/build.yml` last.
2. Open the **Actions** tab → **Build APK** → **Run workflow** (it also runs on every push to `main`).
3. The job runs the unit tests, builds `app-debug.apk` and publishes it under **Releases** (and as a workflow artifact).
4. Open the repository on your phone → **Releases** → download `app-debug.apk` → install (allow "install unknown apps" for your browser when asked).

If the build fails, open the failed step in Actions, copy the red error lines and fix the file they point to.

## Build on a computer

Android Studio (Koala or newer) → Open the folder → Run. Or `gradle assembleDebug` with Gradle 8.7 and JDK 17. Unit tests: `gradle testDebugUnitTest`.
The Gradle wrapper (`gradlew`, `gradle-wrapper.jar`) is not included because the jar is a binary file that cannot be created from the GitHub mobile editor. Android Studio can generate it (`gradle wrapper`).

## Using the app

* **Accounts**: the dashboard lists them; tap one to rename it, change its type or opening balance. `＋ Add account` creates new ones.
* **Quick add**: income, expense, transfer, borrow, lend. Tap any row in History to edit or delete it.
* **Loans tab**: open a loan to record a payment, edit it or delete the whole loan.
* **Settings**: language, theme, PIN, backup, restore, CSV export.

## Privacy and limits

* Data lives only in the app's private storage; `allowBackup` is off so it is not uploaded by Android cloud backup.
* The PIN is a salted SHA-256 hash and only protects against casual access. The JSON backup file is **not encrypted**.
* Forgot the PIN: clear the app's storage in Android settings and restore a backup.
* Export is CSV (not .xlsx); Excel and Google Sheets open it directly.

## বাংলায় সংক্ষেপে

1. GitHub-এ নতুন রিপোজিটরি বানান। প্রতিটি ফাইলের জন্য **Add file → Create new file** চাপুন। ফাইলের নামের ঘরে পুরো পাথ লিখুন (যেমন `app/src/main/java/com/hisab/tracker/Ledger.kt`), `/` দিলে ফোল্ডার নিজে তৈরি হয়।
2. সবশেষে `.github/workflows/build.yml` বানান।
3. **Actions → Build APK → Run workflow** চাপুন। কয়েক মিনিট পর **Releases** থেকে `app-debug.apk` নামিয়ে ইনস্টল করুন।
4. ডেটা শুধু আপনার ফোনে থাকে। নিয়মিত **সেটিংস → ব্যাকআপ নিন** করুন।
