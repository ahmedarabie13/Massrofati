# 📱 Bank SMS Expense & Credit Tracker (Android)

A modern, private, and 100% offline Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room Database**. It securely reads incoming and historical SMS from your bank senders, automatically classifies debits (expenses) vs. credits (income/deposits), and provides detailed financial summaries, bank breakdowns, and CSV export.

---

## 🔒 100% On-Device & Privacy Focused
- **Zero Network Calls**: All processing, parsing, and database storage are performed strictly locally on your Android device.
- **Selective Sender Monitoring**: The app only parses messages from banks you explicitly choose to track (e.g. CIB, NBE, Chase, HSBC, QNB, InstaPay, Vodafone Cash, etc.) and ignores all personal chats.

---

## 🚀 Key Features

1. **📊 Financial Dashboard**:
   - Total Expenses, Total Credits, and Net Cash Flow.
   - Time period selector: *This Month, Last Month, Last 30 Days, This Year, All Time*.
   - One-tap SMS inbox sync with real-time status.
   - Recent transactions feed with color-coded debit/credit badges.

2. **🔍 Transactions Explorer**:
   - Instant search by merchant, keyword, or note.
   - Filter by type (*Debits / Expenses* vs *Credits / Income*).
   - Filter by specific bank.
   - Tap any transaction to view full details (card/account last 4, available balance, timestamp, and the original raw SMS message).

3. **📈 Reports & Visual Analytics**:
   - Expenses by Bank distribution with progress indicators.
   - Category-wise spending breakdown (*Food & Dining, Shopping, Transport, Bills & Utilities, Salary, ATM, Transfers*).
   - Historical monthly cash flow comparison.
   - **CSV Report Export**: Share and export detailed transaction reports via Android's native share sheet.

4. **⚙️ Bank & Sender Discovery**:
   - **Discover Senders**: Automatically scans your SMS inbox to identify potential bank senders and lets you toggle them with a single tap.
   - **Custom Senders**: Add any custom bank or financial sender ID.
   - **Test SMS Parser (Sandbox)**: Live interactive tester to paste any SMS format (English or Arabic) and inspect how the regex engine parses it.

5. **⚡ Real-Time SMS Listener**:
   - Integrated `SmsBroadcastReceiver` that captures incoming bank transaction SMS in real-time as they arrive.

---

## 📂 Project Structure

```
BankSmsExpenseTracker/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # SMS permissions & receivers
│   │   │   ├── java/com/banksms/expensetracker/
│   │   │   │   ├── BankSmsApp.kt            # Application instance & DI
│   │   │   │   ├── MainActivity.kt          # Edge-to-edge Compose host
│   │   │   │   ├── data/
│   │   │   │   │   ├── model/               # Domain entities (Transaction, BankSender, Report)
│   │   │   │   │   ├── local/               # Room Database, DAOs, Entities
│   │   │   │   │   ├── parser/              # BankSmsParser multi-currency & Arabic engine
│   │   │   │   │   ├── reader/              # Telephony SMS ContentResolver inbox scanner
│   │   │   │   │   ├── receiver/            # Real-time BroadcastReceiver for incoming SMS
│   │   │   │   │   └── repository/          # TransactionRepository
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/               # Material 3 colors, typography, theme
│   │   │   │   │   ├── navigation/          # Navigation routes & bottom bar items
│   │   │   │   │   ├── components/          # Reusable cards, badges, date sheet
│   │   │   │   │   ├── screens/
│   │   │   │   │   │   ├── dashboard/       # DashboardScreen & ViewModel
│   │   │   │   │   │   ├── transactions/    # TransactionsScreen & ViewModel
│   │   │   │   │   │   ├── reports/         # ReportsScreen & CsvExporter
│   │   │   │   │   │   ├── senders/         # BankSendersScreen & Parser Sandbox
│   │   │   │   │   │   └── permissions/     # Privacy-focused permission onboarding
│   │   │   │   │   └── MainScreen.kt        # App Scaffold & Navigation Host
│   │   │   │   └── util/                    # CurrencyFormatter, DateUtils
│   │   │   └── res/                         # Strings, colors, icons, XML filepaths
│   │   └── test/
│   │       └── java/com/banksms/expensetracker/
│   │           └── BankSmsParserTest.kt     # Unit tests for bank SMS patterns
│   └── build.gradle.kts
├── gradle/libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ How to Open and Run

1. Open **Android Studio** (Koala / Ladybug or newer recommended).
2. Select **Open** and choose this project folder:
   `/Users/ahmedarabie/.gemini/antigravity/scratch/BankSmsExpenseTracker`
3. Wait for Gradle Sync to complete.
4. Connect your Android phone (via USB debugging or WiFi debugging) or start an Android Emulator.
5. Click **Run (▶)** in Android Studio to install and launch the app.
6. When prompted, grant **SMS Permission** to allow the app to scan your bank messages.
