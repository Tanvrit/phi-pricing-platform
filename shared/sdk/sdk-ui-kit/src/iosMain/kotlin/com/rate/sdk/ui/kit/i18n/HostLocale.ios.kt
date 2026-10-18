package com.rate.sdk.ui.kit.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** Read the user's preferred language using the same language prefix as JVM. */
actual fun detectHostLocale(): String? =
    runCatching { (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') }.getOrNull()
