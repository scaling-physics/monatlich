# Progress

Each milestone ends with a runnable app on `main`, tagged `m<N>`. Check out any tag to see the
app at that stage of evolution. Screenshots live in `docs/screenshots/m<N>/`.

| #  | Milestone                          | Status | Tag  | What you can see                                                        |
|----|------------------------------------|--------|------|-------------------------------------------------------------------------|
| M0 | Toolchain + project scaffold       | ✅     | m0   | Blank app builds and launches in the emulator                           |
| M1 | Theme + navigation shell           | ✅     | m1   | Maroon theme, 3-tab bottom nav, animated month switcher, placeholders   |
| M2 | Data layer (Room, models, repos)   | ✅     | m2   | No visible change; `./gradlew test` green with full repo coverage       |
| M3 | Categories + budgets               | ⬜     |      | Create categories, set budgets, overview shows budget rows              |
| M4 | Add expense + live overview        | ⬜     |      | FAB → sheet → expense; bars animate; remaining updates                  |
| M5 | Transactions tab, edit / delete    | ⬜     |      | Full MVP loop closed                                                    |
| M6 | Currencies                         | ⬜     |      | Base currency, per-transaction USD / EUR / INR, rate table              |
| M7 | Copy budgets + income              | ⬜     |      | New month prompts to copy; month shows net                              |
| M8 | Recurring transactions             | ⬜     |      | Rent / subscriptions auto-appear each month                             |
| M9 | Search / filter, CSV export, backup| ⬜     |      | v1 complete                                                             |

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
