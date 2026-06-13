package com.andrives.geosnap_cam.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.andrives.geosnap_cam.data.model.LocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * LocationService — Provides GPS coordinates, reverse geocoding, and weather data.
 *
 * Direct equivalent of Flutter's GpsService. Uses:
 * - FusedLocationProviderClient for GPS
 * - Android Geocoder for reverse geocoding
 * - Open-Meteo API for weather (via OkHttp)
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "LocationService"
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())

    /**
     * Gets the current location with high accuracy, reverse geocodes it,
     * and fetches weather data — all in parallel.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? = withContext(Dispatchers.IO) {
        try {
            val cancellationSource = CancellationTokenSource()
            val location = suspendCoroutine<android.location.Location?> { cont ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationSource.token,
                ).addOnSuccessListener { loc ->
                    cont.resume(loc)
                }.addOnFailureListener {
                    cont.resume(null)
                }
            } ?: return@withContext null

            val lat = location.latitude
            val lon = location.longitude

            // Parallelize geocoding and weather fetch
            val addressDeferred = async { resolveAddress(lat, lon) }
            val weatherDeferred = async { fetchWeatherSnapshot(lat, lon) }

            val addr = addressDeferred.await()
            val weather = weatherDeferred.await()

            LocationData(
                latitude = lat,
                longitude = lon,
                address = addr.address,
                city = addr.city,
                region = addr.region,
                country = addr.country,
                countryCode = addr.countryCode,
                postalCode = addr.postalCode,
                timezone = weather.timezone,
                temperatureC = weather.temperatureC,
                windKmh = weather.windKmh,
                uvIndex = weather.uvIndex,
            )
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation failed: ${e.message}", e)
            null
        }
    }

    // ── Reverse Geocoding ────────────────────────────────────────────────────

    private data class AddressResult(
        val address: String = "",
        val city: String = "",
        val region: String = "",
        val country: String = "",
        val countryCode: String = "",
        val postalCode: String = "",
    )

    @Suppress("DEPRECATION")
    private suspend fun resolveAddress(lat: Double, lon: Double): AddressResult {
        return try {
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= 33) {
                suspendCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1) { results ->
                        cont.resume(results)
                    }
                }
            } else {
                geocoder.getFromLocation(lat, lon, 1)
            }

            if (addresses.isNullOrEmpty()) return AddressResult()

            val place = addresses[0]

            val street = place.thoroughfare?.trim()
            val number = place.subThoroughfare?.trim()
            val fullStreet = if (!street.isNullOrEmpty() && !number.isNullOrEmpty()) {
                // In some locales it's "123 Main St", in others "Main St 123".
                // Try to detect if number should come first (if street starts with number?)
                // A safe fallback is "$street $number".
                "$street $number"
            } else {
                street ?: number
            }

            val addressParts = listOfNotNull(
                fullStreet?.ifEmpty { null },
                place.subLocality?.trim()?.ifEmpty { null },
            )
            val cityParts = listOfNotNull(
                place.locality?.trim()?.ifEmpty { null },
                place.subAdminArea?.trim()?.ifEmpty { null },
            )

            var address = addressParts.joinToString(", ")
            var city = cityParts.joinToString(", ")
            val region = place.adminArea?.trim() ?: ""

            if (address.isEmpty()) {
                val fallback = listOfNotNull(
                    place.featureName?.trim()?.ifEmpty { null },
                    place.thoroughfare?.trim()?.ifEmpty { null },
                )
                address = fallback.joinToString(", ")
            }
            if (city.isEmpty()) city = region

            AddressResult(
                address = address,
                city = city,
                region = region,
                country = place.countryName?.trim() ?: "",
                countryCode = place.countryCode?.trim() ?: "",
                postalCode = place.postalCode?.trim() ?: "",
            )
        } catch (e: Exception) {
            Log.e(TAG, "resolveAddress failed: ${e.message}")
            AddressResult()
        }
    }

    // ── Weather API ──────────────────────────────────────────────────────────

    private data class WeatherSnapshot(
        val timezone: String = "",
        val temperatureC: Double? = null,
        val windKmh: Double? = null,
        val uvIndex: Double? = null,
    )

    private suspend fun fetchWeatherSnapshot(lat: Double, lon: Double): WeatherSnapshot {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat" +
                    "&longitude=$lon" +
                    "&current=temperature_2m,wind_speed_10m,uv_index" +
                    "&timezone=auto"

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext WeatherSnapshot()

                val body = response.body?.string() ?: return@withContext WeatherSnapshot()
                val json = JSONObject(body)
                val current = json.optJSONObject("current") ?: return@withContext WeatherSnapshot(
                    timezone = json.optString("timezone", ""),
                )

                WeatherSnapshot(
                    timezone = json.optString("timezone", ""),
                    temperatureC = current.optDoubleOrNull("temperature_2m"),
                    windKmh = current.optDoubleOrNull("wind_speed_10m"),
                    uvIndex = current.optDoubleOrNull("uv_index"),
                )
            } catch (e: Exception) {
                Log.e(TAG, "fetchWeather failed: ${e.message}")
                WeatherSnapshot()
            }
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() }
        else null
    }
}
