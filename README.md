# FamilyHome

A household management Android app for families. Track your pantry stock, split chores, log expenses, and get help from a built-in AI assistant — all shared across every family member's phone over your home Wi-Fi.

---

## Table of Contents

- [What is FamilyHome?](#what-is-familyhome)
- [Features](#features)
- [User Roles](#user-roles)
- [How to Use the App](#how-to-use-the-app)
  - [First Launch — Family Setup](#first-launch--family-setup)
  - [Logging In](#logging-in)
  - [Home Dashboard](#home-dashboard)
  - [Pantry & Stock](#pantry--stock)
  - [Chores](#chores)
  - [Expenses](#expenses)
  - [AI Assistant (Chat)](#ai-assistant-chat)
  - [Syncing Between Devices](#syncing-between-devices)
- [Getting the App on Your Phone](#getting-the-app-on-your-phone)
- [For Developers — Running the App](#for-developers--running-the-app)
  - [Prerequisites](#prerequisites)
  - [Clone & Configure](#clone--configure)
  - [Run on a Device or Emulator](#run-on-a-device-or-emulator)
  - [Build a Release APK](#build-a-release-apk)
  - [CI/CD — Automated Builds](#cicd--automated-builds)
- [Tech Stack](#tech-stack)

---

## What is FamilyHome?

FamilyHome is a private, offline-first Android app designed for a single household. There is no cloud account to create and no subscription to pay for. All your data lives on your phones, synced directly between them over your home Wi-Fi.

The app supports three family roles — Father, Wife, and Kid — each with an appropriate level of access. Every family member logs in with their own name and a 4-digit PIN.

---

## Features

| Feature | Description |
|---|---|
| **Pantry & Stock** | Track household items by category. Get alerted when something is running low. |
| **Chores** | Log completed chores and set up recurring tasks with daily or weekly schedules. |
| **Expenses** | Record spending in IDR, categorise it, and see a monthly total. Set budgets with alerts. |
| **AI Assistant** | Chat naturally with a Claude-powered assistant that can log chores, update stock, and record expenses for you. |
| **Family Members** | The Father account manages who is in the family. Each person has their own PIN. |
| **Wi-Fi Sync** | One phone acts as the host; the others connect and pull the latest data — no internet required. |
| **Automated Delivery** | Every push to `main` builds a signed APK and emails it to the family automatically. |

---

## User Roles

| Role | What they can do |
|---|---|
| **Father** | Full access. Creates the family, adds and removes members, manages all data. |
| **Wife** | Can manage pantry, chores, and expenses. Cannot add or remove family members. |
| **Kid** | Can view stock and log chores. Cannot add stock items, delete anything, or see other members' expense details. |

---

## How to Use the App

### First Launch — Family Setup

The very first time the app is opened on any phone, it shows the **Family Setup** screen.

1. Enter the **Father's name**.
2. Choose a **4-digit PIN** and confirm it.
3. Tap **Create Family**.

This creates the Father account and takes you straight into the app. This screen only appears once — on every subsequent launch you will go to the login screen instead.

---

### Logging In

The login screen shows every family member as an avatar card.

1. Tap **your name**.
2. Enter your **4-digit PIN** on the keypad that appears.
3. Tap **Unlock**.

If you enter the wrong PIN you will see an error — just try again. There is no lockout.

> **Adding more family members:** the Father can add Wife and Kid accounts from the Home screen via the Manage Members option. Each new member picks their own name and PIN during setup.

---

### Home Dashboard

After logging in you land on the **Home** screen. It gives you a quick overview of everything that needs attention.

- **Quick access tiles** — tap Pantry, Chores, Expenses, or Chat to jump straight there.
- **Low stock alerts** — any item below its minimum quantity appears here with a warning badge. Tap "See all" to go to the full pantry list.
- **Budget alerts** — if spending in any category has crossed 80 % of its budget, a warning card appears here.
- **Family members** — a list of everyone in the household and their role.
- **Sync button** — the sync icon in the top-right corner opens the Sync Settings screen.

---

### Pantry & Stock

The **Pantry & Stock** screen is your household inventory.

**Browsing stock**

- Items are shown in a scrollable list. Use the **filter chips** at the top to narrow the view by category: Food, Cleaning, Toiletry, or Other.
- Items that are running low show a **Low stock** badge.
- Each row shows the item name, category, current quantity, and unit (e.g. "3.0 kg", "2.0 bottles").

**Adjusting quantities**

- Tap **+** or **−** on any item to increase or decrease its quantity by 1.
- Kids can also adjust quantities — they just cannot add or delete items.

**Adding a new item** _(Father and Wife only)_

1. Tap the **+** button in the bottom-right corner.
2. Fill in the item name, category, starting quantity, unit, and minimum quantity (the threshold that triggers a low-stock alert).
3. Save.

**Deleting an item** _(Father and Wife only)_

Tap the red **bin icon** on an item row to remove it permanently.

---

### Chores

The **Chores** screen has two sections: Recurring Tasks at the top and a History log below.

**Recurring tasks**

Recurring tasks are chores that need to be done on a regular schedule (daily, weekly, or a custom interval). Each task card shows:

- The task name.
- Whether it is **overdue** (shown in red) or **due soon** (shown in blue).
- The frequency (Daily / Weekly / Custom).

Tap **Done** on a task to mark it as completed. This resets its countdown to the next due date and adds an entry to the history log.

**Logging a one-off chore**

Tap the **+** button to open the quick-log dialog:

1. Type the **task name** (e.g. "Washed the car").
2. Add an optional **note**.
3. Tap **Log**.

The chore is saved immediately with a timestamp and your name.

**History**

The history section shows chores completed in the last 7 days by default. Tap **Show 30d** to expand the view to the last 30 days, or **Show 7d** to return to the shorter view.

**Adding a recurring task** _(Father and Wife only)_

Tap the manage/add button to create a new recurring task, set its frequency, and optionally assign it to a specific family member.

---

### Expenses

The **Expenses** screen tracks household spending in Indonesian Rupiah (IDR).

**Monthly summary**

A card at the top of the screen shows the total amount spent in the current calendar month.

**Budget alerts**

If a budget limit has been set for a category and spending has exceeded roughly 80 % of it, a warning card appears below the summary. The same warning also shows as a badge on the top-right icon.

**Logging an expense**

Tap the **+** button to open the Add Expense dialog:

1. Enter the **amount in IDR** (e.g. `50000` for Rp 50,000).
2. Write a short **description** (e.g. "Weekly groceries").
3. Select a **category**: Groceries, Transport, School, Health, Entertainment, Household, or Other.
4. Tap **Save**.

The expense is recorded immediately with today's date and your name. Expenses logged by the AI Assistant are marked with a small **"via AI"** label.

**Setting a budget** _(Father only)_

Budgets can be set per category (or as an overall monthly limit) from the budget management section. When spending approaches or exceeds the limit, the app shows a warning on both the Home dashboard and the Expenses screen.

---

### AI Assistant (Chat)

The **AI Assistant** screen is a chat interface powered by Claude. You can talk to it naturally and it will take actions inside the app on your behalf.

**What the assistant can do**

| You say | What happens |
|---|---|
| "We finished washing the dishes" | Logs a chore called "wash dishes" |
| "Update rice to 2 kg" | Sets the pantry rice quantity to 2 |
| "I spent 75000 on transport today" | Records a Transport expense of Rp 75,000 |
| "What's low in the pantry?" | Gives you a summary of low-stock items |
| "How much have I spent this month?" | Tells you your total spending for the current month |
| "Show me the chore history" | Summarises recent completed chores |

**Role-aware responses**

The assistant knows who is logged in. A Kid will only be able to see and modify their own data. A Father or Wife gets full household access.

**How to use it**

1. Type your message in the text field at the bottom and tap the **Send** button.
2. The assistant replies in the chat bubble above. If it takes an action (like logging a chore), it will confirm what it did in its reply.
3. The conversation history is kept for the session. Closing and reopening the Chat screen starts a fresh conversation.

> **Note:** The AI Assistant requires an Anthropic API key to function. If no key is configured, the assistant will display a setup message instead of responding.

---

### Syncing Between Devices

FamilyHome syncs data between phones **over your local Wi-Fi network** — no internet connection is required. One phone acts as the **host (server)** and the others connect to it as **clients**.

**Setting up the host (usually Dad's phone)**

1. From the Home screen, tap the **Sync** icon in the top-right corner.
2. Under **Host Mode**, tap **Start Server**.
3. The screen will show the phone's local IP address (e.g. `192.168.1.5`) and confirm the server is running on port `8765`.
4. Leave the app open on this phone while others sync.

**Connecting as a client (everyone else)**

1. Open Sync Settings on your phone.
2. Under **Connect to Host**, enter the host phone's IP address (e.g. `192.168.1.5`).
3. Tap **Save**, then tap **Sync Now**.
4. The status card at the top will show "Connected" and display the last sync time once it completes.

**Tips**

- All devices must be on the same Wi-Fi network.
- Sync is triggered manually — tap **Sync Now** whenever you want to pull the latest data.
- The host server stops automatically when the app is closed or when you tap **Stop Server**.

---

## Getting the App on Your Phone

If you received an email from FamilyHome Updates, a new version has been built automatically.

1. Open the email and tap the green **Download FamilyHome APK** button.
2. On the release page that opens, tap the `.apk` file to download it.
3. Pull down your notification bar and tap the downloaded file.
4. If Android shows a warning ("blocked by Play Protect" or asks for permission), go to **Settings → Install unknown apps**, enable it for your browser, then go back and try again.
5. Tap **Install**. Your existing data and PIN are preserved.
6. Open **FamilyHome**, tap your name, enter your PIN, and you are in.

---

## For Developers — Running the App

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK** with API level 35 installed
- An **Anthropic API key** (for the AI chat feature — get one at [console.anthropic.com](https://console.anthropic.com))
- A physical Android device (API 26+) or an emulator

### Clone & Configure

```bash
git clone <your-repo-url>
cd family-app
```

Copy the template and fill in your values:

```bash
cp local.properties.template local.properties
```

Edit `local.properties`:

```properties
# Android SDK path — Android Studio fills this in automatically
sdk.dir=/Users/YourName/Library/Android/sdk

# Anthropic API key — required for the AI chat feature
ANTHROPIC_API_KEY=sk-ant-your-key-here
```

> `local.properties` is git-ignored and must **never** be committed. The API key is embedded into the APK at build time via `BuildConfig`.

### Run on a Device or Emulator

Open the project in Android Studio and click **Run**, or use the command line:

```bash
./gradlew installDebug
```

The app will be installed and launched on the connected device or running emulator.

### Build a Release APK

To build a signed release APK locally you need a keystore file. Run:

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/your.jks \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=YOUR_KEY_ALIAS \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

The signed APK will be output to `app/build/outputs/apk/release/`.

### CI/CD — Automated Builds

Every push to the `main` branch triggers the **Build & Deliver** GitHub Actions workflow, which:

1. Checks out the code and sets up JDK 17.
2. Extracts the `versionName` from `app/build.gradle.kts`.
3. Decodes the release keystore from the `KEYSTORE_BASE64` secret and builds a signed release APK.
4. Creates a **GitHub Release** tagged `v{versionName}-build.{run_number}` and attaches the APK.
5. Sends a styled HTML email to the family with a direct download link and installation instructions.

**Required GitHub repository secrets**

| Secret | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key baked into the release APK |
| `KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w 0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |
| `GMAIL_ADDRESS` | Gmail address used to send the family email |
| `GMAIL_APP_PASSWORD` | Gmail App Password (not your account password) |
| `FAMILY_EMAILS` | Comma-separated list of recipient email addresses |

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| DI | Hilt |
| Local database | Room |
| Preferences | DataStore |
| Wi-Fi sync | Ktor Server (CIO) + Ktor Client (OkHttp) |
| AI chat | Anthropic Claude API via Retrofit |
| Serialization | Kotlinx Serialization |
| Image loading | Coil |
| Language | Kotlin (JVM 17) |
| Min Android | API 26 (Android 8.0 Oreo) |
