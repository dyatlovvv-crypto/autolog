package ru.autolog.app.data.map

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import org.json.JSONArray
import org.json.JSONObject

/**
 * Station catalogue from https://gdebenz.ru (same API as the site / bot).
 * Requires short-lived [X-RT] from `/api/rt`.
 */
object GdebenzStationsClient {
    private const val TAG = "AutologMap"
    private const val BASE = "https://gdebenz.ru"
    private const val HTTP_UA =
        "Autolog/0.8.2 (Android; fuel map; contact=github.com/dyatlovvv-crypto/autolog)"

    private val rtToken = AtomicReference<String?>(null)
    private val rtExpiresAtMs = AtomicReference(0L)

    /** Fast pass: [/api/nearby] only. */
    fun fetchNearbyElementsJson(lat: Double, lon: Double, radiusKm: Int): String? {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("gdebenz cancelled")
        }
        ensureRt()
        return queryNearby(lat, lon, radiusKm.coerceIn(1, 20))
    }

    /** Expand: [/api/stations] bbox around [lat]/[lon]. */
    fun fetchBboxElementsJson(lat: Double, lon: Double, radiusKm: Int): String? {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("gdebenz cancelled")
        }
        ensureRt()
        return queryStationsBbox(lat, lon, radiusKm.coerceIn(1, 80))
    }

    /** Nearby + bbox merge (one-shot). */
    fun fetchElementsJson(lat: Double, lon: Double, radiusKm: Int): String? {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("gdebenz cancelled")
        }
        ensureRt()
        val nearbyKm = radiusKm.coerceIn(1, 20)
        val nearby = queryNearby(lat, lon, nearbyKm)
        val bbox = queryStationsBbox(lat, lon, radiusKm.coerceIn(1, 80))
        val merged = FuelStationsJson.mergeById(nearby, bbox ?: return nearby, lat, lon)
        return merged.takeIf { FuelStationsJson.hasGeoElements(it) }
    }

    private fun ensureRt() {
        val now = System.currentTimeMillis()
        if (!rtToken.get().isNullOrBlank() && now < rtExpiresAtMs.get() - 30_000L) return
        val raw = httpGet("$BASE/api/rt", withRt = false) ?: return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val rt = o.optString("rt").takeIf { it.isNotBlank() } ?: return
        val ttlSec = o.optLong("ttl", 1_800L).coerceIn(60L, 7_200L)
        rtToken.set(rt)
        rtExpiresAtMs.set(now + ttlSec * 1_000L)
        Log.d(TAG, "gdebenz rt ok ttl=$ttlSec")
    }

    private fun queryNearby(lat: Double, lon: Double, radiusKm: Int): String? {
        val url =
            "$BASE/api/nearby?lat=${fmt(lat, 5)}&lon=${fmt(lon, 5)}" +
                "&radius_km=$radiusKm"
        val raw = httpGet(url, withRt = true) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val arr = root.optJSONArray("stations") ?: return null
        val json = FuelStationsJson.fromGdebenzArray(arr) ?: return null
        Log.d(TAG, "gdebenz nearby≈${FuelStationsJson.elementCount(json)} r=${radiusKm}km")
        return json
    }

    private fun queryStationsBbox(lat: Double, lon: Double, radiusKm: Int): String? {
        val (lat1, lon1, lat2, lon2) = bbox(lat, lon, radiusKm.toDouble())
        val url =
            "$BASE/api/stations?lat1=${fmt(lat1, 4)}&lon1=${fmt(lon1, 4)}" +
                "&lat2=${fmt(lat2, 4)}&lon2=${fmt(lon2, 4)}"
        val raw = httpGet(url, withRt = true) ?: return null
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val json = FuelStationsJson.fromGdebenzArray(arr) ?: return null
        Log.d(TAG, "gdebenz bbox≈${FuelStationsJson.elementCount(json)} r=${radiusKm}km")
        return json
    }

    private fun bbox(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ): Quad {
        val dlat = radiusKm / 111.32
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val dlon = radiusKm / (111.32 * cosLat)
        return Quad(lat - dlat, lon - dlon, lat + dlat, lon + dlon)
    }

    private data class Quad(val lat1: Double, val lon1: Double, val lat2: Double, val lon2: Double)

    private fun fmt(v: Double, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", v)

    private fun httpGet(url: String, withRt: Boolean): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", HTTP_UA)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Referer", "$BASE/")
                if (withRt) {
                    rtToken.get()?.takeIf { it.isNotBlank() }?.let {
                        setRequestProperty("X-RT", it)
                    }
                }
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
            if (code == 401 || code == 403) {
                rtToken.set(null)
                rtExpiresAtMs.set(0L)
            }
            if (code in 200..299 && !body.isNullOrBlank()) body else {
                Log.w(TAG, "gdebenz GET ${url.substringAfter(BASE)} -> $code")
                null
            }
        } catch (t: Throwable) {
            if (t is InterruptedException) throw t
            Log.w(TAG, "gdebenz fail: ${t.message}")
            null
        }
    }
}
