# AGENT.md — monatlich

**monatlich** (German: "monthly") is an offline-first Android app for tracking monthly budgets.
Personal tool first; structure kept clean enough for a future Play Store release.

## Product

### Core model: category budgets per month
- A **Month** (e.g. 2026-09) is the top-level unit. The current month is the home screen.
- **Categories** (Groceries, Rent, Transport, …) are global and reusable across months.
- Each month, a category gets a **budget amount**. Budgets can be copied from the previous month.
- **Transactions** are logged against a category with an amount, date, and optional note.
  Expenses reduce a category's remaining budget; income entries feed the month's net total.
- The month overview shows per-category *spent / budget / remaining* and a month total.

### Multi-currency
- Supported from the start: **USD, EUR, INR** (list is extensible; currencies are ISO 4217 codes).
- One **base currency** (app setting). All totals, "remaining", and comparisons are computed in it.
- Every transaction and every budget stores its own `currencyCode`. A month may mix currencies
  (e.g. ₹ expenses against a € Groceries budget while travelling).
- Each transaction stores the **exchange rate to base currency at entry time** (`rateToBase`),
  so history never shifts when rates change later.
- Rates come from a user-editable **rate table** (base → each currency). Offline only; fetching
  live rates is a possible later opt-in, never a requirement.
- Changing the base currency re-derives nothing retroactively; it only affects new entries and
  the display of totals (converted via stored rates).

### Feature roadmap

**MVP — usable day to day**
- [x] Month navigation (prev / next / jump to current)
- [x] Categories: create, rename, reorder, archive; icon + color
- [x] Set budget amount per category per month
- [x] Add expense: amount, category, date (defaults to today), note. Entry must be *fast*.
- [x] Month overview: per-category spent/budget/remaining, total spent vs. total budget
- [x] Transaction list per month and per category; edit / delete
- [x] Currencies: base-currency setting, per-transaction currency (USD / EUR / INR), rate table

**v1 — makes it stick**
- [x] Copy last month's budgets into a new month (prompt when opening an empty month)
- [x] Income transactions → month shows net (income − expenses)
- [x] Recurring transactions (rent, subscriptions) auto-created each month
- [x] Search / filter transactions
- [x] Export to CSV
- [x] Local backup / restore (full DB export + import)

**Later — nice to have**
- [x] Charts: spend by category (month), month-over-month trend
- [x] Home-screen widget for quick add
- [x] Unspent-budget rollover into next month (per-category opt-in)
- [ ] Opt-in live exchange rate fetch
- [ ] Add more currencies beyond USD / EUR / INR
- [x] Biometric / PIN app lock
- [ ] Localization: English + German
- [ ] Play Store prep: onboarding, privacy policy, accessibility pass

**Explicitly out of scope**
- Bank / account syncing
- User accounts, login, cloud sync
- Ads, analytics, telemetry of any kind

## UI direction

The app should feel **smooth and polished**: fluid, purposeful motion; nothing jumps or snaps into
place. Information architecture is fixed now (expensive to change later); visual detail can evolve.

- **Home = current month overview.** Category rows with spent / budget / remaining bars, month total
  at the top, month switcher in the top bar.
- **One-tap add.** A FAB on the home screen opens the add-expense sheet. Amount field is focused
  with the keyboard up immediately; category and date are one tap each. Target: log an expense in
  under 5 seconds.
- **Three top-level destinations** (bottom nav): Overview · Transactions · Settings.
  Categories and budgets are managed from within Overview, not a separate tab.

### Theme
- **Brand color: deep maroon.** Seed `#6B1F2A`; primary highlights (FAB, active nav item, progress
  bars, selected chips, links) are maroon. Tonal palette generated via Material 3 color roles from
  that seed — a single `Color.kt` + `Theme.kt` under `ui/theme/`.
- **Dynamic color (Material You) is OFF** so the maroon identity is consistent across devices.
- Neutral surfaces: warm off-white in light mode, warm near-black in dark mode (not pure `#000`),
  so maroon reads as rich rather than harsh. Dark mode follows the system setting.
- Semantic colors: over-budget = the M3 `error` role (keep it distinct from maroon — use a
  cooler/brighter red); on-track = a muted green; both tuned to pass contrast on the surfaces above.
- Typography: Material 3 type scale, default system font for now. Amounts use tabular figures
  (`FontFeatureSettings "tnum"`) so columns line up.

### Motion
- Every state change animates. Use Compose animation APIs, never instant swaps:
  - Numbers (totals, remaining) count up/down with `animateIntAsState` / custom `Animatable`.
  - Budget progress bars animate width on load and on change (`animateFloatAsState`, ~400 ms,
    `FastOutSlowInEasing`).
  - Lists use `LazyColumn` with stable `key`s and `Modifier.animateItem()` for add / remove / reorder.
  - Expanding rows / cards use `animateContentSize()`.
  - Screen transitions: shared-axis (horizontal for month switching, vertical for drill-down) via
    Navigation Compose `enterTransition` / `exitTransition`. Swiping horizontally between months
    is a first-class gesture.
  - Add-expense sheet: `ModalBottomSheet` with spring-based entry; amount keypad appears with the
    sheet, not after it.
- Durations: 150–200 ms for small feedback, 300–400 ms for layout changes, never > 500 ms.
  Respect the system "remove animations" accessibility setting (`LocalAccessibilityManager` /
  `Settings.Global.ANIMATOR_DURATION_SCALE`).
- Performance is part of "smooth": no dropped frames on the home screen. Keep composables
  skippable (stable params, immutable `UiState`), avoid recomposition storms, use
  `derivedStateOf` for computed values. Verify with the Layout Inspector's recomposition counts
  before merging animation-heavy screens.

## Tech stack

| Concern         | Choice                                              |
|-----------------|-----------------------------------------------------|
| Language        | Kotlin                                              |
| UI              | Jetpack Compose + Material 3                        |
| Architecture    | MVVM, unidirectional data flow (UI state via `StateFlow`) |
| DI              | Hilt                                                |
| Persistence     | Room (SQLite), offline only                         |
| Async           | Kotlin coroutines + Flow                            |
| Navigation      | Navigation Compose                                  |
| Build           | Gradle 9.7 Kotlin DSL, AGP 9.4 (built-in Kotlin — no separate `kotlin-android` plugin), version catalog (`gradle/libs.versions.toml`) |
| SDK             | minSdk 26 · targetSdk 37 · compileSdk 37             |
| Testing         | JUnit + Turbine for ViewModels/repos; Room in-memory tests; Compose UI tests for key screens |

## Project layout (target)

```
app/src/main/java/com/monatlich/
  data/
    local/         Room database, entities, DAOs
    repository/    Repository implementations
  domain/
    model/         Plain Kotlin models (Month, Category, Budget, Transaction)
    repository/    Repository interfaces
    usecase/       One class per use case where logic is non-trivial
  ui/
    theme/
    navigation/
    <feature>/     One package per screen: Screen composable + ViewModel + UiState
  di/              Hilt modules
```

## Conventions

- **Money is a `Money(amountMinor: Long, currency: Currency)` value type.** Minor units (cents/paise) as
  `Long`, never `Double`/`Float`. Format at the UI edge only, using the currency's locale conventions
  (`€1.234,56` vs `$1,234.56` vs `₹1,23,456.00`).
- Never add two `Money` values of different currencies directly; convert to base via `rateToBase` first.
- Exchange rates are stored as `BigDecimal` strings (Room `TEXT`), never as `Double`.
- **Months are keyed as `YearMonth`** (`java.time`), stored in Room as an ISO string `"2026-09"`.
- Dates use `java.time.LocalDate`; no `java.util.Date`.
- Categories are soft-deleted (`archived = true`) so historical transactions keep their category.
- Every screen owns a single `UiState` data class exposed as `StateFlow` from its ViewModel;
  composables are stateless and take `(state, onEvent)`.
- Repositories expose `Flow` for reads and `suspend` functions for writes.
- No business logic in composables or DAOs — put it in use cases or the ViewModel.
- Room schema changes require a migration and a schema export (`room.schemaLocation`); never use
  `fallbackToDestructiveMigration` outside debug builds.
- Prefer small, focused commits. Commit messages: imperative mood, first line ≤ 72 chars.

## Working in this repo

Toolchain on this machine (set up 2026-09-13):
- Android SDK at `~/Library/Android/sdk` (`local.properties` points there; not committed).
- Gradle runs on JDK 17 from `/opt/homebrew/opt/openjdk@17/...` via `org.gradle.java.home` in
  `~/.gradle/gradle.properties`. Android Studio's bundled JDK 25 also works with Gradle 9.
- Emulator AVD `monatlich_pixel` (Pixel 8, API 36, arm64). Start:
  `~/Library/Android/sdk/emulator/emulator -avd monatlich_pixel &`
- Screenshots: `adb exec-out screencap -p > docs/screenshots/m<N>/<name>.png`;
  toggle dark mode with `adb shell cmd uimode night yes|no`.

- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (needs emulator/device)
- Lint: `./gradlew lint`
- Run tests before declaring a change done. If a test can't run (no emulator), say so.
- When adding a feature, tick its box in the roadmap above in the same commit.
- Keep this file current: if a decision here changes, update it here first.
