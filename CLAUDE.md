# FamilyHome — Claude Code Project Instructions

## Project Overview

FamilyHome is an Android family management app built with Kotlin and Jetpack Compose. It tracks household stocks, chores, expenses, and Islamic prayer goals for a family unit. A father device acts as a sync server over local Wi-Fi; other family members connect as clients.

## Architecture: Clean Architecture (3-layer)

```
Presentation  ←  Domain  ←  Data
(ViewModel/UI)   (UseCases  (Room DB,
                 + Models)   Repositories,
                             Mappers, Sync)
```

- **Domain** — pure Kotlin, no Android imports. Contains models, repository interfaces, use cases, and `PermissionManager`.
- **Data** — Room entities, DAOs, mappers (entity↔domain↔DTO), repository implementations, Ktor sync, notifications.
- **Presentation** — Hilt ViewModels, Compose screens, navigation graph.

## Tech Stack

| Concern | Library |
|---|---|
| Language | Kotlin 2.1.20 |
| UI | Jetpack Compose + Material3 |
| DI | Hilt 2.55 |
| Local DB | Room 2.6.1 |
| Networking (sync) | Ktor 2.3.12 (CIO server + OkHttp client) |
| REST (AI) | Retrofit 2.11.0 + Kotlinx Serialization |
| Coroutines | kotlinx-coroutines 1.8.1 |
| Image | Coil 2.7.0 |
| Charts | Vico 1.15.0 |
| Session | DataStore 1.1.1 |

## Module Structure

Single module project (`:app`). Key packages:

```
com.familyhome.app
├── agent/              # Anthropic Claude API integration
├── data/
│   ├── local/
│   │   ├── dao/        # Room DAOs (UserDao, ExpenseDao, StockItemDao, …)
│   │   ├── database/   # FamilyDatabase (v5) + migrations
│   │   └── entity/     # Room entities
│   ├── mapper/         # Extension fns: Entity↔Domain↔DTO
│   ├── notification/   # AlarmScheduler, LowStockNotifier, NotificationCenter
│   ├── onboarding/     # NSD, OnboardingServer/Client
│   ├── repository/     # Repository implementations
│   ├── service/        # FamilyBackgroundService, BootReceiver
│   ├── session/        # SessionRepositoryImpl (DataStore)
│   └── sync/           # SyncClient, DeletionTracker, PresenceTracker
├── di/                 # Hilt modules (AppModule, DatabaseModule, NetworkModule, RepositoryModule)
├── domain/
│   ├── model/          # Data classes + enums (User, Expense, Budget, StockItem, …)
│   ├── permission/     # PermissionManager (role-based access)
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic grouped by feature
│       ├── chore/
│       ├── expense/
│       ├── stock/
│       └── user/
└── presentation/
    ├── components/     # Shared Compose components
    ├── navigation/     # NavGraph, Screen sealed class
    ├── screens/        # Feature screens + ViewModels
    └── theme/          # Material3 theming
```

## Key Domain Models

- **User** — `id, name, role (FATHER|WIFE|KID), parentId?, avatarUri?, pin (SHA-256), createdAt`
- **Expense** — amount in IDR cents, category, paidBy userId, optional receipt URI
- **Budget** — limitAmount per user/category/period (MONTHLY|WEEKLY)
- **StockItem** — name, category, quantity, minQuantity (triggers low-stock alert when `quantity <= minQuantity`)
- **RecurringTask** — chore scheduled with frequency, optional alarm reminder
- **ChoreAssignment** — assigns a task to a user; status: PENDING → ACCEPTED|DECLINED
- **PrayerGoalSetting** — sunnah goal enabled for specific users; comma-separated assignedTo in DB
- **PrayerLog** — one row per (userId, sunnahKey, epochDay) with `completedCount`

## Coding Conventions

### Naming
- Files: PascalCase matching class name
- Packages: lowercase, feature-first (`usecase/chore/`, `screens/expenses/`)
- Use cases: `<Verb><Noun>UseCase` — `LogExpenseUseCase`, `SetBudgetUseCase`
- DAOs: `<Entity>Dao` — `UserDao`, `ExpenseDao`
- Mappers: extension functions in `data/mapper/` — `fun Entity.toDomain()`, `.toDto()`, `.toEntity()`
- ViewModels: `<Screen>ViewModel` — `LoginViewModel`, `ExpensesViewModel`
- UI State: `<Screen>UiState` data class colocated with ViewModel

### DI Pattern
All repositories are bound via `@Binds @Singleton` in `RepositoryModule`. ViewModels use `@HiltViewModel`. Database and DAOs provided via `@Provides` in `DatabaseModule`.

### Flows
- DAOs return `Flow<List<Entity>>` for reactive queries
- ViewModels expose `StateFlow` via `stateIn(WhileSubscribed(5_000))`
- Use `turbine` for Flow assertions in tests

### Role-based Access
Always check via `PermissionManager` — never inline role checks in ViewModels. PermissionManager is a pure `object` in the domain layer.

## Adding New Features (Step-by-step)

1. **Room Entity** — add `@Entity` data class in `data/local/entity/`
2. **DAO** — add `@Dao` interface in `data/local/dao/`; return `Flow<List<>>` for queries
3. **Register in FamilyDatabase** — add to `entities = [...]` and add `abstract fun newFeatureDao(): NewFeatureDao`
4. **Migration** — bump DB version, add `val MIGRATION_N_N1` in `DatabaseModule`, register it
5. **Mapper** — add extension functions in `data/mapper/NewFeatureMapper.kt`
6. **Domain Model** — add data class / enum in `domain/model/`
7. **Repository Interface** — add interface in `domain/repository/`
8. **Repository Impl** — implement in `data/repository/`; bind in `RepositoryModule`
9. **Use Cases** — add in `domain/usecase/<feature>/`; inject repository + PermissionManager checks
10. **ViewModel** — add in `presentation/screens/<feature>/`; inject use cases; expose `StateFlow<UiState>`
11. **Screen** — add Compose screen; collect state with `collectAsStateWithLifecycle()`
12. **Navigation** — add route to `Screen` sealed class and `NavGraph`
13. **Tests** — unit tests for use cases + mappers; DAO integration tests; optional UI tests

## Running Tests & Quality Tools

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Detekt (static analysis)
./gradlew detektAll

# JaCoCo coverage report
./gradlew jacocoTestReport

# JaCoCo coverage verification (enforces 80% line coverage)
./gradlew jacocoTestCoverageVerification

# PITest mutation testing (domain + data layers only)
./gradlew pitest

# Full quality gate (Detekt + tests + coverage + mutation)
./gradlew qualityCheck
```

## Agent Guidelines

### Software Architecture Agent
- Before writing code, read all affected files.
- Never bypass `PermissionManager` checks.
- Maintain Clean Architecture — no Android imports in `domain/`.
- When adding a new DB column: write the migration; don't use `exportSchema = false` as a shortcut for breaking changes in production.
- Keep `SyncPayload` and DTOs serializable (`@Serializable`).

### QA Agent
- Unit tests live in `app/src/test/` and use JUnit5 + MockK.
- Integration/DAO tests live in `app/src/androidTest/` and use Room in-memory DB.
- UI tests live in `app/src/androidTest/` and use Compose testing + Hilt.
- Every use case must have tests for: happy path, permission denied (each role), and edge cases.
- Every mapper must test round-trips: entity→domain→entity, dto→domain→dto.
- DAO tests must cover: insert, query, update, delete, and the unique-constraint/index behavior.

## Common Pitfalls

- **`runCatching { EnumClass.valueOf(str) }`** — always use this pattern when parsing enums from DB strings (see `ExpenseMapper`, `StockMapper`). A bad string must fall back to `OTHER`, never crash.
- **`assignedUserIds` in `PrayerGoalSettingEntity`** is stored as a comma-separated String (`"id1,id2"`); split/join manually — Room doesn't support List columns directly.
- **`ChoreRepositoryImpl.upsertAllAssignments`** preserves local ACCEPTED/DECLINED status — never blindly overwrite with incoming PENDING from sync.
- **`LowStockNotifier`** depends on Android context — mock it in unit tests; only call real implementation in integration tests.
- **`AlarmScheduler`** depends on `AlarmManager` — mock in use case tests; test scheduling logic in isolation.
- **`DeletionTracker`** is injected into `DeleteBudgetUseCase` — must be mocked in those tests.
- **PIN hashing** — always SHA-256; never store plaintext PIN. Test `ValidatePinUseCase` against known hash values.
- **`System.currentTimeMillis()`** in use cases makes tests time-dependent — use `assertThat(result.createdAt).isAtLeast(before)` pattern instead of exact equality.
- **Coroutine test dispatcher** — use `StandardTestDispatcher` + `advanceUntilIdle()` for ViewModels.

## Git Commit Conventions (Conventional Commits)

```
<type>(<scope>): <short description>

Types: feat | fix | test | refactor | docs | chore | perf | ci | build
Scope: domain | data | ui | chore | expense | stock | prayer | sync | auth

Examples:
feat(expense): add receipt photo capture to expense log
fix(sync): preserve assignment status on incoming PENDING upsert
test(usecase): add permission tests for ChoreUseCases
chore(deps): bump Room to 2.7.0
```
