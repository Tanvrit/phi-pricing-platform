# sdk-ui-kit

The **design system + i18n home** for the whole UI layer of the re-architected
PHI platform. This is the lowest module in the `sdk-ui-*` tier: it owns the visual
language (tokens, theme, reusable components) and the customer-journey string
catalog. Every other `-ui` module (`sdk-ui-operator`, `sdk-ui-buyonline`) depends
on it and reuses its component API rather than re-rolling widgets.

Package root: `com.rate.sdk.ui.kit`.

## Layering

```
core → sdk → sdk-ui-kit → sdk-ui-operator / sdk-ui-buyonline → app(:aegis)
```

- **Compose Multiplatform lives ONLY in `sdk-ui-*` modules.** This module is the
  first to pull in `compose.runtime/foundation/material3/materialIconsExtended`.
- Pure-KMP otherwise: it depends on `:shared:core:core` (for `Money` /
  regulatory tokens) plus the Ktor *client* (so kit-level networked widgets stay
  possible). NO Mongo driver, NO Ktor-server, NO Apache POI.
- Targets: `jvm()` (operator desktop console) + `wasmJs { browser() }`
  (customer journey on Cloudflare Pages).

## What's inside

### `theme/` — the Aegis (operator) design system
- `AegisColors` — `AegisColorTokens` data class with `LightColors` / `DarkColors`
  palettes, threaded via `LocalAegisColors`; read at callsites through the
  `AegisColors` accessor object (`AegisColors.brand`, `AegisColors.canvas`, …).
  **Never hardcode hex outside `AegisColors.kt`.**
- `AegisTypography` (display/h1-3/body/money/mono scales), `AegisSpacing` (4px
  base), `AegisRadii`, `AegisElevation`, `AegisMotion` (+ `LocalReducedMotion`).
- `AegisTheme(dark, locale, content)` — wraps Material3 against the active
  palette and provides `LocalAegisColors` + `LocalAegisLocale`. Unlike the old
  monolith it does **not** read any settings store; the host (operator shell /
  buyonline app) decides `dark` + `locale` from its own persistence and passes
  them in.

### `components/` — reusable Aegis primitives
Buttons (`AegisButton`), inputs (`AegisInput`, `AegisMoneyField`), `AegisCard`,
`AegisTable` (+ `AegisColumn`, `TableDensity`), `AegisTopBar`, `AegisShell`
(rail + top bar + toast host, driven by generic `AegisNavItem`s),
`AegisSideNav`, `AegisDrawer`, `AegisCommandPalette` (⌘K), toasts
(`AegisToastHost` + `showToast`), plus chips, badges, callouts, status pills,
tabs, breadcrumbs, dividers, kbd chips, sparklines, empty states.

> **Compose pitfall baked into `AegisTable`:** it renders a plain `Column` when
> `maxHeight == null` and only a `LazyColumn` when a finite `maxHeight` is given.
> Operator surfaces wrap content in a page `verticalScroll`; a nested unbounded
> `LazyColumn` crashes with *"Vertically scrollable component was measured with
> an infinity maximum height"*. **Rule: never nest a lazy/unbounded scroller
> inside a `verticalScroll` without a bounded height.**

### `brand/` — the PRU customer (buyonline) brand
A separate consumer-grade visual language (Prudential red). `PruTheme.kt` holds
the `Pru*` colour tokens + `PRUHealthTheme`; `PruComponents.kt` holds the branded
journey primitives (`PRUTopBar`, `ProgressTabs`, `PRUButton`, `StickyPriceBar`,
`InfoBanner`, `ErrorBanner`, `ChipSelector`, `PopularBadge`, `MemberSummaryBar`).
`sdk-ui-buyonline` builds its 22-screen journey on these.

### `i18n/` — EN/HI string catalog + locale
- `AegisLocale` (`EN`, `HI`) + `Strings` (dotted-key catalog; Hindi misses fall
  back to English, English misses fall back to the key).
- `LocalAegisLocale` + `@Composable t("landing.cta")` shortcut.
- `detectHostLocale()` — `expect`/`actual` host-locale probe
  (JVM `Locale.getDefault().language`, wasmJs `navigator.language`). Read once at
  startup by the platform `main` to auto-seed the persisted locale; not per-frame.

### `di/`
`uiKitModule()` — a minimal Koin module (the kit is stateless today; the module
keeps the DI surface uniform and gives future kit singletons a home).

## Relocated from

The old monolith's `aegis/src/.../com/rate/aegis/{theme,components,i18n}` and
`customer/buyonline/ui/{theme,components}`. Repackaged `com.rate.aegis.*` →
`com.rate.sdk.ui.kit.*` and `com.rate.domain.money.Money` →
`com.rate.core.money.Money`. App-coupled bits were left behind:
`NotificationDropdown` (needs the app `Notification` type) and the
settings-store read inside `AegisTheme` (now an explicit parameter). `AegisShell`
was decoupled from the app-specific `AegisSurface` enum and now takes generic
`AegisNavItem`s.
