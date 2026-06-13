package com.andrives.geosnap_cam.util

/**
 * Converts a 2-letter ISO country code to a flag emoji.
 * Example: "CL" → "🇨🇱", "US" → "🇺🇸"
 */
object FlagEmoji {

    fun fromCountryCode(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val code = countryCode.uppercase()
        return buildString {
            for (char in code) {
                // Regional Indicator Symbol: 0x1F1E6 is 🇦, offset by char - 'A'
                appendCodePoint(0x1F1E6 + (char - 'A'))
            }
        }
    }
}
