package com.andrives.geosnap_cam.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.andrives.geosnap_cam.data.model.WatermarkMapType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * MapTileService — Downloads and composites map tiles from OSM/ArcGIS/OpenTopoMap.
 *
 * Direct equivalent of Flutter's MapTileService.
 * Uses Android Bitmap/Canvas instead of dart:ui for tile compositing.
 */
@Singleton
class MapTileService @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "MapTileService"
        private const val MAP_ZOOM = 17
        private const val MAP_TILE_SIZE = 256
        private const val MAP_VIEWPORT_SIZE = 512
    }

    private val mapCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

    /**
     * Returns a cached map image if available.
     */
    fun getCachedMapImage(
        latitude: Double,
        longitude: Double,
        mapType: WatermarkMapType,
    ): Bitmap? {
        val tile = latLonToTileLocation(latitude, longitude, MAP_ZOOM)
        return mapCache[cacheKey(mapType, tile)]
    }

    /**
     * Returns a cached map image or fetches a new one.
     */
    suspend fun getOrFetchMapImage(
        latitude: Double,
        longitude: Double,
        mapType: WatermarkMapType,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val tile = latLonToTileLocation(latitude, longitude, MAP_ZOOM)
        val key = cacheKey(mapType, tile)

        mapCache[key]?.let { return@withContext it }

        try {
            val image = buildCenteredMapImage(mapType, tile) ?: return@withContext null
            synchronized(mapCache) {
                mapCache[key] = image
                // Evict oldest if cache too large
                if (mapCache.size > 60) {
                    val oldest = mapCache.keys.first()
                    mapCache.remove(oldest)?.recycle()
                }
            }
            image
        } catch (e: Exception) {
            Log.e(TAG, "getOrFetchMapImage failed: ${e.message}")
            null
        }
    }

    // ── Tile compositing ─────────────────────────────────────────────────────

    private suspend fun buildCenteredMapImage(
        mapType: WatermarkMapType,
        center: TileLocation,
    ): Bitmap? {
        val viewportHalf = MAP_VIEWPORT_SIZE / 2.0
        val firstTileX = ((center.worldPixelX - viewportHalf) / MAP_TILE_SIZE).toInt()
        val lastTileX = ((center.worldPixelX + viewportHalf) / MAP_TILE_SIZE).toInt()
        val firstTileY = ((center.worldPixelY - viewportHalf) / MAP_TILE_SIZE).toInt()
        val lastTileY = ((center.worldPixelY + viewportHalf) / MAP_TILE_SIZE).toInt()
        val originWorldX = center.worldPixelX - viewportHalf
        val originWorldY = center.worldPixelY - viewportHalf

        val bitmap = Bitmap.createBitmap(MAP_VIEWPORT_SIZE, MAP_VIEWPORT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var drewAnyTile = false

        for (x in firstTileX..lastTileX) {
            for (y in firstTileY..lastTileY) {
                if (y < 0 || y > center.maxTile) continue

                val wrappedX = wrapTileX(x, center.maxTile + 1)
                val tileImage = fetchTileImage(mapType, wrappedX, y, MAP_ZOOM) ?: continue

                val dx = (x * MAP_TILE_SIZE) - originWorldX
                val dy = (y * MAP_TILE_SIZE) - originWorldY
                val dst = Rect(
                    dx.toInt(), dy.toInt(),
                    (dx + MAP_TILE_SIZE).toInt(), (dy + MAP_TILE_SIZE).toInt(),
                )
                val src = Rect(0, 0, tileImage.width, tileImage.height)
                canvas.drawBitmap(tileImage, src, dst, paint)
                tileImage.recycle()
                drewAnyTile = true
            }
        }

        if (!drewAnyTile) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private suspend fun fetchTileImage(
        mapType: WatermarkMapType,
        x: Int, y: Int, z: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = buildTileUrl(mapType, x, y, z)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GeoSnapCam/1.0 (https://github.com/Andr-studio/GeoSnap-Camera)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bytes = response.body?.bytes() ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // ── Tile math ────────────────────────────────────────────────────────────

    private data class TileLocation(
        val worldPixelX: Double,
        val worldPixelY: Double,
        val maxTile: Int,
    )

    private fun latLonToTileLocation(lat: Double, lon: Double, zoom: Int): TileLocation {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val n = 2.0.pow(zoom)
        val worldSize = n * MAP_TILE_SIZE
        val worldPixelX = ((lon + 180.0) / 360.0) * worldSize
        val latRad = Math.toRadians(clampedLat)
        val worldPixelY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * worldSize

        return TileLocation(
            worldPixelX = worldPixelX.coerceIn(0.0, worldSize - 1),
            worldPixelY = worldPixelY.coerceIn(0.0, worldSize - 1),
            maxTile = n.toInt() - 1,
        )
    }

    private fun wrapTileX(x: Int, tileCount: Int): Int {
        return ((x % tileCount) + tileCount) % tileCount
    }

    private fun cacheKey(mapType: WatermarkMapType, tile: TileLocation): String {
        return "${mapType.key}:$MAP_ZOOM:${tile.worldPixelX.roundToInt()}:${tile.worldPixelY.roundToInt()}"
    }

    private fun buildTileUrl(mapType: WatermarkMapType, x: Int, y: Int, z: Int): String {
        return when (mapType) {
            WatermarkMapType.SATELLITE ->
                "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            WatermarkMapType.TERRAIN ->
                "https://a.tile.opentopomap.org/$z/$x/$y.png"
            WatermarkMapType.STANDARD ->
                "https://tile.openstreetmap.org/$z/$x/$y.png"
        }
    }
}
