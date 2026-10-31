package com.cinch.shared.language

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun deviceLanguageCode(): String = NSLocale.currentLocale.languageCode()
