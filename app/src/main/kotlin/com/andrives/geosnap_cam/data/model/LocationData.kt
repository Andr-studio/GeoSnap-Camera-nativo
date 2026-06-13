package com.andrives.geosnap_cam.data.model

/**
 * Location data including GPS coordinates, address, and weather information.
 * Direct equivalent of Flutter's LocationData class.
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val countryCode: String = "",
    val postalCode: String = "",
    val timezone: String = "",
    val temperatureC: Double? = null,
    val windKmh: Double? = null,
    val uvIndex: Double? = null,
) {
    /**
     * Title line: "Region, City, Country"
     */
    val title: String
        get() = listOfNotNull(
            region.trim().ifEmpty { null },
            city.trim().ifEmpty { null },
            country.trim().ifEmpty { null },
        ).joinToString(", ")

    /**
     * Address line with postal code: "Calle 123, 1234567"
     */
    val addressWithPostal: String
        get() = listOfNotNull(
            address.trim().ifEmpty { null },
            postalCode.trim().ifEmpty { null },
        ).joinToString(", ")

    companion object {
        /** Preview/default location for settings screen when GPS is unavailable */
        val PREVIEW = LocationData(
            latitude = -23.604062,
            longitude = -70.377349,
            address = "Pasaje Ejemplo 1234",
            city = "Ejemplopolis",
            region = "Ejemploregion",
            country = "Ejemplopais",
            countryCode = "CL",
            postalCode = "1234567",
            timezone = "America/Santiago",
            temperatureC = 17.9,
            windKmh = 9.1,
            uvIndex = 0.0,
        )
    }
}
