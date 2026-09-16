# Progress

Each milestone ends with a runnable app on `main`, tagged `m<N>`. Check out any tag to see the
app at that stage of evolution. Screenshots live in `docs/screenshots/m<N>/`.

| #  | Milestone                          | Status | Tag  | What you can see                                                        |
|----|------------------------------------|--------|------|-------------------------------------------------------------------------|
| M0 | Toolchain + project scaffold       | ✅     | m0   | Blank app builds and launches in the emulator                           |
| M1 | Theme + navigation shell           | ✅     | m1   | Maroon theme, 3-tab bottom nav, animated month switcher, placeholders   |
| M2 | Data layer (Room, models, repos)   | ✅     | m2   | No visible change; `./gradlew test` green with full repo coverage       |
| M3 | Categories + budgets               | ✅     | m3   | Create categories, set budgets, overview shows budget rows              |
| M4 | Add expense + live overview        | ✅     |      | FAB → sheet → expense; bars animate; remaining updates                  |
| M5 | Transactions tab, edit / delete    | ✅     |      | Full MVP loop closed                                                    |
| M6 | Currencies                         | ✅     |      | Base currency, per-transaction USD / EUR / INR, rate table              |
| M7 | Income                             | ✅     |      | Month shows net (copy-budgets prompt shipped early in M3)                |
| M8 | Recurring transactions             | ✅     |      | Rent / subscriptions auto-appear each month                             |
| M9 | Search / filter, CSV export, backup| ✅     |      | v1 complete                                                             |

Legend: ⬜ not started · 🔄 in progress · ✅ done

## Workflow

1. Each milestone is a branch `m<N>-<slug>` off `main`.
2. Independent work within a milestone runs as parallel agents in separate git worktrees
   (`git worktree add`), each on its own sub-branch; they must not touch the same files.
3. Integration: merge sub-branches → run `./gradlew assembleDebug testDebugUnitTest lint` → fix →
   merge to `main` → tag `m<N>` → tick the row above and add screenshots.
4. `main` is always runnable. Never merge red.

## Log

- 2026-09-13 — Project brief written (`AGENT.md`).
- 2026-09-13 — **M0 done.** Android Studio + SDK 36/37 + JDK 17 installed; Gradle 9.7 / AGP 9.4 /
  Kotlin 2.3 / Compose BOM 2026.09 / Hilt 2.60 / Room 2.8 scaffold builds, unit + instrumented
  tests pass, app launches on emulator with maroon theme in light and dark.
  Screenshots: `docs/screenshots/m0/`.
- 2026-09-13 — **M1 + M2 done** (built in parallel by two agents in worktrees, merged same day).
  M1: nav shell (Overview · Transactions · Settings), month switcher with swipe + shared-axis
  animation, animated budget bars and count-up totals, FAB → add sheet, reduced-motion support,
  INR lakh-aware formatter. M2: Room v1 schema (categories, budgets, transactions, exchange_rates),
  `Money`/`Currency` domain types, repositories, `GetMonthSummary` use case, Hilt modules, seed
  categories. Merged `main`: 54 unit + 16 instrumented tests green. Screenshots: `docs/screenshots/m1/`.
- 2026-09-13 — **M3 done** (three parallel agents: overview-on-real-data + settings, category
  manager, budget sheet + copy prompt; integrated by hand). Overview now reads Room via
  `GetMonthSummary`; tap a category → set-budget sheet (calculator-style entry, USD/EUR/INR);
  new empty month → "copy budgets from last month?"; Settings has base currency, editable rate
  table, and Manage categories (add/rename/reorder/archive, icon + colour). Integration fixes:
  merged two duplicate icon/badge helpers into `ui/common`, auto-focus in the category editor,
  real badge in the budget sheet. 110 unit + 21 instrumented tests green. Screenshots: `docs/screenshots/m3/`.
- 2026-09-14 — **M4–M9 done in one pass** (M8 recurring transactions had already shipped ahead of
  turn in an earlier session; this pass filled in the M4–M7 gap it left and closed out M9). The
  data layer (Room, repositories) from M2/M8 already covered nearly everything needed, so this was
  mostly UI: `TransactionEditorSheet` (add/edit expense or income — amount keypad, currency chips,
  type toggle, category chips, Today/Yesterday/date-picker, note) reused from the FAB on Overview
  and from a new FAB on Transactions; Transactions tab replaced its placeholder with a real
  per-month list (date-grouped, swipe-to-delete with undo, search box, type/category filter chips,
  tap-to-edit); Overview's total card gained an income/net row once any income is logged; Settings
  gained a "Manage data" drill-down (M9) with CSV export and a raw-SQLite-file backup/restore
  (checkpoint-and-copy on backup; close-overwrite-restart on restore, via
  `Intent.makeRestartActivityTask` + process kill) — both driven by the Storage Access Framework,
  no new dependency. Manually verified end-to-end on the emulator: add → overview total updates →
  transactions list shows it → edit → swipe-delete-with-undo → CSV export produced a real file →
  backup produced a valid SQLite file → restore correctly closed the DB, overwrote it, and the app
  came back up cleanly on Overview. 150 unit + 58 instrumented tests green.
  Screenshots: `docs/screenshots/m4-m9/`.
- 2026-09-15 — **Two "nice to have" items done: charts and a home-screen widget.** Charts: a new
  `GetMonthlyTrend` use case (six months of base-currency spend/income, live) sits alongside the
  existing `GetMonthSummary`; a new "Insights" screen (drill-down from Settings, same pattern as
  Categories/Recurring/Data) shows a donut of this month's spend by category and a paired
  spend/income bar chart for the trend — both hand-drawn with Compose `Canvas`, no charting
  dependency. Widget: `androidx.glance` (new dependency) backs a home-screen widget showing the
  month's spend and remaining budget, with a "+" that deep-links straight into the add-transaction
  sheet (`MainActivity` reads a `quick_add` intent extra and auto-opens
  `OverviewEvent.AddExpenseClicked`); a `WidgetRefresher` pushes a Glance update after every
  transaction save/delete so the widget doesn't wait for its periodic refresh. Manually verified on
  the emulator: Insights donut/trend match Overview's totals; widget pinned to the home screen
  renders live data and its "+" opens the add sheet with the keyboard ready. 158 unit + 29
  instrumented tests green, lint clean.
- 2026-09-16 — **Biometric / PIN app lock.** A 4-digit PIN (salted SHA-256, never the raw PIN) gates
  the app on cold start and on returning from the background; a new "App lock" screen under Settings
  sets/changes/removes the PIN and toggles lock and biometric unlock independently. `MainActivity`
  became a `FragmentActivity` (required by `androidx.biometric.BiometricPrompt`) and now hosts the
  lock gate ahead of `MonatlichAppShell`; a `ProcessLifecycleOwner` observer re-locks the app after
  30s+ in the background, with a grace window so a quick trip through the CSV/backup file picker
  doesn't force a re-unlock. Caught and fixed a real bug along the way: the lock ViewModel is
  Activity-scoped and outlives any one lock-screen showing, so modeling "unlocked" as a persistent
  boolean in its `uiState` went stale — the next time the screen had to show again, it would
  immediately auto-dismiss itself. Fixed by making the unlock a one-shot `SharedFlow` event instead
  (regression test added). Manually verified end-to-end on the emulator: set PIN → lock auto-enables
  → wrong PIN rejected → correct PIN unlocks → quick background/foreground doesn't re-lock →
  30s+ background does. 174 unit + 29 instrumented tests green, lint clean.
- 2026-09-16 — **Unspent-budget rollover (cumulative saving across months).** Categories get a new
  opt-in "Roll over unspent budget" toggle (`rolloverEnabled`, Room migration 2→3); when on, a
  category's unspent budget — or overspend, carried as a negative balance — accumulates across
  every prior month rather than resetting, so a "Travel" category can build up savings for months
  before a big trip. `GetMonthSummary` now also pulls every category's full budget/spend history
  (`observeAllTotalsByCategoryAndMonthInBase`) to fold into a running `carryInBase` per category,
  added on top of that month's own budget; Overview's category rows show a "+X rolled over" /
  "-X rolled over (overspent)" caption under the progress bar. Caught and fixed a real bug before
  it shipped: `Seed.kt`'s raw-SQL seed insert (used only on first database creation, so it bypasses
  the normal `@Insert` path and its Kotlin-side defaults) didn't set the new NOT NULL
  `rolloverEnabled` column, which would have crashed every fresh install; found via instrumented
  test failures, fixed, reverified. Manually verified end-to-end on the emulator: enabled rollover
  on Groceries, set an August budget of 100 with a 30 expense, then confirmed September (with no
  budget set) showed 70 available with a "+70,00 € rolled over" caption. 184 unit + 30 instrumented
  tests green, lint clean.
