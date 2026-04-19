# FamilyHome 🏠

> A private, offline-first Android household management app — built for Muslim families who want to stay organised without cloud accounts or subscriptions.

Track your pantry, split chores, log expenses, monitor Sunnah prayer goals, and get help from a built-in AI assistant — all synced directly between family phones over your home Wi-Fi.

---

## Table of Contents

- [What is FamilyHome?](#what-is-familyhome)
- [Features](#features)
- [Screenshots](#screenshots)
- [Architecture Overview](#architecture-overview)
- [App Flow](#app-flow)
- [Data Model](#data-model)
- [Wi-Fi Sync Architecture](#wi-fi-sync-architecture)
- [AI Agent Architecture](#ai-agent-architecture)
- [Navigation Map](#navigation-map)
- [User Roles & Permissions](#user-roles--permissions)
- [Tech Stack](#tech-stack)
- [Getting Started (Developers)](#getting-started-developers)
- [CI/CD Pipeline](#cicd-pipeline)
- [How to Use the App](#how-to-use-the-app)

---

## What is FamilyHome?

FamilyHome is a **private, self-hosted household manager** built with Jetpack Compose. There is no cloud backend, no account sign-up, and no subscription fee.

- All data lives on your family's phones
- Synced directly over your local Wi-Fi (no internet required)
- Designed for Muslim households with built-in Sunnah tracking
- Claude-powered AI assistant that takes actions inside the app

---

## Features

| Module | What it does |
|---|---|
| 🥫 **Pantry & Stock** | Track household items by category. Low-stock alerts when quantity drops below your threshold. |
| 🧹 **Chores** | Log one-off tasks and recurring chores (daily/weekly). Full history log with timestamps. |
| 💸 **Expenses** | Record spending in IDR with categories and monthly budgets. Budget alerts at 80% threshold. |
| 🕌 **Prayer & Sunnah** | Track family Sunnah prayer goals with daily reminders and Islamic calendar events. |
| 🤖 **AI Assistant** | Natural language chat powered by Claude Haiku. Can log chores, update stock, and record expenses. |
| 👨‍👩‍👧 **Family Members** | Role-based access (Father / Wife / Kid) with 4-digit PIN authentication per member. |
| 📡 **Wi-Fi Sync** | One phone acts as host server; others sync over local network. No internet required. |
| 🔔 **Notifications** | Prayer reminders, low stock alerts, budget warnings, and chore due alerts. |
| 📦 **Auto Delivery** | Every push to `main` builds a signed APK and emails it to the family. |

---

## Architecture Overview

FamilyHome follows **Clean Architecture** with a strict separation between layers:

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│   Jetpack Compose Screens + ViewModels (MVVM)               │
│   Screens: Home, Stock, Chores, Expenses, Prayer, Chat      │
└───────────────────────┬─────────────────────────────────────┘
                        │ observes StateFlow / events
┌───────────────────────▼─────────────────────────────────────┐
│                      Domain Layer                            │
│   Use Cases · Repository Interfaces · Domain Models         │
│   Pure Kotlin — no Android dependencies                      │
└───────────────────────┬─────────────────────────────────────┘
                        │ implements interfaces
┌───────────────────────▼─────────────────────────────────────┐
│                       Data Layer                             │
│   Room Database · DAOs · Entities · Mappers                 │
│   Sync (Ktor Server + Client) · Anthropic API (Retrofit)    │
│   Notifications · DataStore · Onboarding                    │
└─────────────────────────────────────────────────────────────┘
```

**Dependency Injection:** Hilt provides all dependencies across layers.

---

## App Flow

```
         ┌─────────────────────────────────────────┐
         │              App Launch                  │
         └──────────────────┬──────────────────────┘
                            │
              Has family data in DB?
               /                    \
             NO                     YES
              │                      │
    ┌─────────▼──────────┐  ┌───────▼────────┐
    │   Setup Screen      │  │  Login Screen   │
    │ (Create Father acct)│  │ (PIN entry)     │
    └─────────┬──────────┘  └───────┬────────┘
              │                      │
    ┌─────────▼──────────┐  ┌───────▼────────┐
    │ Father Onboarding   │  │  Home Dashboard │◄──────────────┐
    └─────────┬──────────┘  └───────┬────────┘               │
              │                     │                          │
    ┌─────────▼──────────┐          │ navigate to             │
    │  Tutorial Screen    │   ┌──────▼──────┬──────────────┐  │
    └─────────┬──────────┘   │             │              │  │
              │              │             │              │  │
              └──────────────►  Stock   Chores  Expenses  │  │
                             │  Prayer   Chat   Members   │  │
                             └──────────────────────┬─────┘  │
                                                    │         │
                                              back button ────┘
```

---

## Data Model

```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│    User       │       │    StockItem      │       │    Expense        │
│──────────────│       │──────────────────│       │──────────────────│
│ id (PK)      │       │ id (PK)          │       │ id (PK)          │
│ name         │       │ name             │       │ amount (IDR)     │
│ role         │◄──┐   │ category         │       │ description      │
│ parentId(FK) │   │   │ quantity         │       │ category         │
│ pin (SHA-256)│   │   │ unit             │       │ userId (FK) ─────┼──► User
│ avatarUri    │   │   │ minQuantity      │       │ loggedAt         │
│ createdAt    │   │   │ updatedAt        │       │ isViaAgent       │
└──────────────┘   │   └──────────────────┘       └──────────────────┘
                   │
┌──────────────────┼───┐       ┌──────────────────┐
│  ChoreAssignment  │   │       │    PrayerLog      │
│──────────────────│   │       │──────────────────│
│ id (PK)          │   │       │ id (PK)          │
│ title            │   │       │ userId (FK) ──────┼──► User
│ assignedUserId ──┼───┘       │ sunnahKey        │
│ frequency        │           │ epochDay         │
│ nextDueAt        │           │ completedCount   │
│ isRecurring      │           │ loggedAt         │
└──────────────────┘           └──────────────────┘

┌──────────────────┐       ┌──────────────────┐
│     Budget        │       │    ChoreLog       │
│──────────────────│       │──────────────────│
│ id (PK)          │       │ id (PK)          │
│ category         │       │ title            │
│ limitAmount(IDR) │       │ userId (FK) ──────┼──► User
│ createdBy (FK) ──┼──►User│ completedAt      │
└──────────────────┘       │ note             │
                           └──────────────────┘
```

---

## Wi-Fi Sync Architecture

FamilyHome uses an embedded **Ktor HTTP server** on the host device (Father's phone) and a Ktor HTTP client on all member devices.

```
          ┌─────────────────────────────────────┐
          │         HOST DEVICE (Father)         │
          │                                     │
          │  ┌─────────────┐  ┌───────────────┐ │
          │  │  SyncServer  │  │OnboardingServer│ │
          │  │  (Ktor CIO)  │  │  (join flow)  │ │
          │  │  port 8765   │  │  port 8765    │ │
          │  └──────┬──────┘  └───────┬───────┘ │
          │         │                 │          │
          │  ┌──────▼─────────────────▼───────┐  │
          │  │         Room Database           │  │
          │  │  Users · Stock · Chores ·       │  │
          │  │  Expenses · Prayer · Budget     │  │
          │  └─────────────────────────────────┘  │
          └─────────────────────┬────────────────┘
                                │ local Wi-Fi
              ┌─────────────────┼──────────────────┐
              │                 │                  │
   ┌──────────▼──────┐ ┌────────▼────────┐ ┌──────▼───────────┐
   │  Member Device 1 │ │  Member Device 2 │ │  Member Device 3 │
   │  (Wife)          │ │  (Kid)           │ │  (new member)    │
   │                  │ │                  │ │                  │
   │  SyncClient      │ │  SyncClient      │ │  OnboardingClient│
   │  POST /sync      │ │  POST /sync      │ │  POST /join      │
   │  ← full payload  │ │  ← full payload  │ │  ← approval wait │
   └──────────────────┘ └──────────────────┘ └──────────────────┘

Sync Payload:
  • All users, stock, chores, chore logs
  • Expenses, budgets, custom categories
  • Prayer logs, prayer goal settings
  • Deletion tombstones (DeletionTracker)
  • Member presence heartbeats
```

**Sync is manual** — tap "Sync Now" on any client device. The host server runs as long as the app is open.

---

## AI Agent Architecture

```
  User types message
         │
         ▼
  ┌─────────────┐
  │  ChatScreen  │
  │  (Compose)   │
  └──────┬──────┘
         │ calls
         ▼
  ┌─────────────┐
  │ ChatViewModel│
  └──────┬──────┘
         │ calls
         ▼
  ┌─────────────────────────────────────────────────┐
  │               FamilyAgent                        │
  │                                                  │
  │  1. Append user message to conversation history  │
  │  2. Build system prompt (role-aware)             │
  │  3. POST to Anthropic API (Claude Haiku)         │
  │                                                  │
  │  ┌─────────────────────────────────────────┐    │
  │  │  Tool loop (max 5 iterations)            │    │
  │  │                                          │    │
  │  │  Response has tool_use blocks?           │    │
  │  │      YES → dispatch via AgentTools       │    │
  │  │           → append tool_result           │    │
  │  │           → call Claude again            │    │
  │  │      NO  → extract text reply → done    │    │
  │  └─────────────────────────────────────────┘    │
  └──────────────────────┬──────────────────────────┘
                         │
         ┌───────────────▼────────────────┐
         │          AgentTools             │
         │                                │
         │  log_chore    → ChoreRepo      │
         │  update_stock → StockRepo      │
         │  log_expense  → ExpenseRepo    │
         │  get_stock    → StockRepo      │
         │  get_expenses → ExpenseRepo    │
         │  get_chores   → ChoreRepo      │
         └────────────────────────────────┘
```

The agent is **role-aware** — the system prompt is tailored to the logged-in user's role. A Kid can only view/log their own data. Father and Wife get full household access.

---

## Navigation Map

```
                    ┌──────────┐
                    │  Setup   │ (first launch only)
                    └────┬─────┘
                         │
              ┌──────────▼─────────┐
              │  Father Onboarding  │
              └──────────┬─────────┘
                         │
              ┌──────────▼─────────┐
              │     Tutorial        │
              └──────────┬─────────┘
                         │
              ┌──────────▼─────────┐
              │       Login         │ ◄── every subsequent launch
              └──────────┬─────────┘
                         │
              ┌──────────▼─────────────────────────────────┐
              │                 Home                         │
              │  (dashboard: alerts, tiles, member list)     │
              └──┬─────┬──────┬──────┬──────┬──────┬───────┘
                 │     │      │      │      │      │
              Stock  Chores Expense Prayer Chat  Settings
                 │                              │
           AddStock                    ┌────────┴────────┐
                                       │                 │
                                  ManageMembers    SyncSettings
                                       │
                                  MemberOnboarding (new member joins)
```

---

## User Roles & Permissions

| Action | Father | Wife | Kid |
|---|:---:|:---:|:---:|
| View stock | ✅ | ✅ | ✅ |
| Adjust stock quantity | ✅ | ✅ | ✅ |
| Add / delete stock items | ✅ | ✅ | ❌ |
| Log chores | ✅ | ✅ | ✅ |
| Manage recurring tasks | ✅ | ✅ | ❌ |
| Log expenses | ✅ | ✅ | ❌ |
| View all expenses | ✅ | ✅ | ❌ |
| Set budgets | ✅ | ❌ | ❌ |
| Add / remove family members | ✅ | ❌ | ❌ |
| Approve member join requests | ✅ | ❌ | ❌ |
| Set prayer goals for family | ✅ | ❌ | ❌ |
| Log own prayer goals | ✅ | ✅ | ✅ |
| Use AI assistant | ✅ | ✅ | ✅ (limited) |
| Start sync server (host) | ✅ | ✅ | ✅ |

---

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.1.20 |
| UI | Jetpack Compose + Material 3 | BOM 2024.09.00 |
| Architecture | MVVM + Clean Architecture | — |
| DI | Hilt | 2.55 |
| Navigation | Compose Navigation | 2.7.7 |
| Local DB | Room | 2.6.1 |
| Preferences | DataStore | 1.1.1 |
| Wi-Fi Sync | Ktor Server (CIO) + Client (OkHttp) | 2.3.12 |
| AI Chat | Anthropic Claude API via Retrofit | 2.11.0 |
| Serialization | Kotlinx Serialization JSON | 1.7.3 |
| Coroutines | Kotlinx Coroutines | 1.8.1 |
| Image loading | Coil | 2.7.0 |
| Build | AGP | 8.7.0 |
| Min Android | API 26 (Android 8.0 Oreo) | — |

---

## Getting Started (Developers)

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (bundled with Android Studio)
- Android SDK with API level 35
- An Anthropic API key — [get one here](https://console.anthropic.com)
- Physical Android device (API 26+) or emulator

### Clone & Configure

```bash
git clone <your-repo-url>
cd family-app
cp local.properties.template local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/Users/YourName/Library/Android/sdk
ANTHROPIC_API_KEY=sk-ant-your-key-here
```

> ⚠️ `local.properties` is git-ignored. Never commit your API key.

### Run

```bash
./gradlew installDebug
```

Or open in Android Studio and click **Run**.

### Build Release APK

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/your.jks \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=YOUR_KEY_ALIAS \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

Output: `app/build/outputs/apk/release/`

---

## CI/CD Pipeline

```
  git push → main
       │
       ▼
  GitHub Actions: "Build & Deliver"
       │
       ├─ 1. Checkout + Setup JDK 17
       ├─ 2. Extract versionName from build.gradle.kts
       ├─ 3. Decode KEYSTORE_BASE64 secret → release.jks
       ├─ 4. ./gradlew assembleRelease (signed APK)
       ├─ 5. Create GitHub Release (tag: v{version}-build.{N})
       │      └─ Attach signed APK as release asset
       └─ 6. Send HTML email to family
              └─ "Download FamilyHome APK" button → GitHub Release

Required Secrets:
  ANTHROPIC_API_KEY   · KEYSTORE_BASE64  · KEYSTORE_PASSWORD
  KEY_ALIAS           · KEY_PASSWORD     · GMAIL_ADDRESS
  GMAIL_APP_PASSWORD  · FAMILY_EMAILS
```

---

## How to Use the App

### First Launch

1. Enter the **Father's name** and choose a **4-digit PIN**
2. Tap **Create Family**
3. Complete the onboarding + tutorial
4. From Home → Manage Members to add Wife and Kids

### Adding Family Members

New members open the app → tap "Join existing family" → enter the host IP → Father approves the join request on his device.

### Daily Use

| Want to... | Go to... |
|---|---|
| Check what's low in the pantry | Home dashboard or Stock screen |
| Log a chore quickly | Chores → + button |
| Record an expense | Expenses → + button |
| Ask the AI to do it for you | Chat screen |
| Sync everyone's data | Home → Sync icon → Sync Now |
| Track Sunnah prayers | Prayer screen |

### AI Assistant Examples

```
"We finished washing the dishes"       → logs chore
"Update rice to 2 kg"                  → updates stock
"I spent 75000 on transport today"     → records expense
"What's low in the pantry?"            → shows low stock summary
"How much have we spent this month?"   → shows monthly total
```

---

*Built with ❤️ for Muslim families. No cloud, no subscriptions, no data leaks.*
