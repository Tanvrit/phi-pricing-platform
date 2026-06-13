package com.rate.aegis

/**
 * The `AEGIS_DEV_PROFILE` dev-bypass flag.
 *
 * When true the [AegisRoot] auth gate is skipped entirely: the console boots
 * straight into its role (OWNER on desktop) with NO login required — the fast
 * inner-loop for local development and the WASM preview. When false (the default,
 * and the only safe posture for any real deploy) operators hit [com.rate.sdk.ui.operator.surface.LoginSurface]
 * first and must present valid credentials before the console renders.
 *
 * Resolution is a platform seam:
 *   JVM  — the `AEGIS_DEV_PROFILE` env var or `-Daegis.devProfile=true` system property.
 *   WASM — the `dev=1` / `devProfile=1` URL query flag (so a preview link can opt in),
 *          off for the bare public URL.
 *
 * The customer (buy-online) journey is unaffected — it is OTP-gated server-side and
 * never reaches this operator gate.
 */
expect val aegisDevProfile: Boolean
