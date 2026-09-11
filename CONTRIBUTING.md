# Contributing

## Branching

| Branch     | Purpose                                                            |
|------------|--------------------------------------------------------------------|
| `main`     | Always green. Tagged for releases. Production-ready.               |
| `dev`      | Integration branch; PRs merge here first.                          |
| `feat/*`   | Feature work. Branch from `dev`, PR back to `dev`.                 |
| `fix/*`    | Bug fixes. Branch from `dev` (or `main` for hotfixes).             |
| `chore/*`  | Build, deps, infra, CI. Branch from `dev`.                         |

## Commit message style

Conventional Commits — short imperative subject (≤ 72 chars), optional body explaining
the *why* (not the *what*).

```
feat(engine): apply 18% GST to QuoteResult.totalIncludingGst

The engine previously returned pre-tax totals. Indian health insurance
attracts 18% GST under HSN 9971. Every UI surface treated the engine
output as the headline figure, so customers saw an under-quoted number.
```

Prefixes: `feat` · `fix` · `chore` · `docs` · `test` · `refactor` · `perf` · `style` · `ci` · `build` · `security`.

## Pull-request checklist

- [ ] Branch up-to-date with `dev`
- [ ] `./gradlew build` is green (CI mirror) — needs a headless Chrome on `CHROME_BIN` for `wasmJsBrowserTest`; see [docs/08-development.md](docs/08-development.md#build-all-modules)
- [ ] `./gradlew :shared:jvmTest` passes
- [ ] New behaviour has a test pinning it
- [ ] No `println` in production code (use SLF4J)
- [ ] No plaintext secrets, no hardcoded paths
- [ ] No `runCatching` with silent fallbacks that fabricate user-visible data
- [ ] If schema changed: a new `Vn__*.sql` migration was added (never edit prior migrations)
- [ ] If a customer-visible string changed: PRD/copy reviewer tagged
- [ ] `CHANGELOG.md` "Unreleased" updated under the relevant heading

## Local pre-push

```bash
make test    # ./gradlew test
make build   # ./gradlew build -x test
             #   -x test skips only tasks literally NAMED `test`, so the
             #   19 modules with `wasmJs { browser() }` still run
             #   wasmJsBrowserTest and still need a headless Chrome on
             #   CHROME_BIN — see docs/08-development.md
```

## Architecture rules (please follow)

1. **No parallel pricing implementations.** All premium math goes through
   `com.rate.domain.engine.PricingEngine`. If you find yourself writing a
   `tier * base * (1 - discount)` expression anywhere outside the engine, stop.
2. **No `Double` for money totals.** Use `com.rate.domain.money.Money`. Cover *rates*
   (fractions) remain `Double`; *totals*, *line items*, *amounts* are `Money` or are
   exposed via `Money.toRupees()` only for display.
3. **No silent fallbacks** in UI or VM code. If an API call fails, surface the error.
   Never fabricate proposal numbers, hospital counts, OTPs, or premiums.
4. **No plaintext PII in logs.** The Logback PII-masking converter covers the obvious
   ones (Aadhaar / mobile / PAN) — but don't rely on it; do not log raw customer data.
5. **No new migrations that drop data.** All schema changes are additive. To rename or
   drop a column, do it in a multi-step migration with explicit data backfill.
6. **All validators live in `:shared`.** Server, desktop, and buy-online share one
   regex per field. Don't reimplement in routes or VMs.

## Code style

- Kotlin official style (`.editorconfig` enforces).
- Targeted ktlint / detekt in CI (failures block merge).
- 4-space indent; 120-char line limit.
- No tabs.
- Prefer expression bodies for one-liners.
- Comments explain *why*, not *what* — well-named code already says what.

## Reporting issues

File issues with:
- Module (`:shared` / `:server` / `:desktop` / `:buyonline`)
- Reproducible steps (curl, Gradle command, screenshot)
- Expected vs actual
- Stack trace if any
- Affected branch + commit SHA
