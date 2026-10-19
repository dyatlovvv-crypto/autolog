package ru.autolog.app.data.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import org.json.JSONArray
import org.json.JSONObject

/**
 * Prefetches nearby fuel stations so the map opens with data ready.
 * Primary: https://gdebenz.ru `/api/nearby` + `/api/stations` (same catalogue as the site).
 * Fallback: Overpass / Nominatim / Photon if gdebenz is down.
 */
class NearbyFuelStationsStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fuel-stations").apply { isDaemon = true }
    }
    private val prefetchRunning = AtomicBoolean(false)

    @Volatile
    private var cache: Cache? = null

    data class Cache(
        val lat: Double,
        val lon: Double,
        val json: String,
        val fetchedAtEpochMs: Long,
        val radiusM: Int = QUERY_RADIUS_M,
    )

    fun cachedOrNull(maxAgeMs: Long = CACHE_TTL_MS): Cache? {
        val c = cache ?: return null
        if (c.radiusM < FAST_RADIUS_M) return null
        if (System.currentTimeMillis() - c.fetchedAtEpochMs > maxAgeMs) return null
        // Drop broken cache from older builds (`out tags` without lat/lon).
        if (!FuelStationsJson.hasGeoElements(c.json)) return null
        return c
    }

    /** App-scoped IO — survives map screen dispose (avoids RejectedExecutionException). */
    fun execute(block: () -> Unit) {
        runCatching { io.execute(block) }
            .onFailure { Log.w(TAG, "execute: ${it.message}") }
    }

    /** Test / UI-test helper: put stations in memory so the map opens without network. */
    fun seedCacheForTests(lat: Double, lon: Double, json: String) {
        cache = Cache(
            lat = lat,
            lon = lon,
            json = json,
            fetchedAtEpochMs = System.currentTimeMillis(),
            radiusM = QUERY_RADIUS_M,
        )
    }

    /** Start background fetch from last known location (no-op if already fresh / in flight). */
    fun prefetchAsync() {
        if (cachedOrNull() != null) return
        if (!prefetchRunning.compareAndSet(false, true)) return
        io.execute {
            try {
                val loc = lastKnownLocation() ?: Location("fallback").apply {
                    latitude = 55.75
                    longitude = 37.62
                }
                val nearby = cachedOrNull()?.takeIf {
                    distanceMeters(it.lat, it.lon, loc.latitude, loc.longitude) < REUSE_RADIUS_M
                }
                if (nearby != null) return@execute
                fetchStationsProgressive(loc.latitude, loc.longitude, onPartial = null)
            } catch (t: Throwable) {
                Log.w(TAG, "prefetch fail: ${t.message}")
            } finally {
                prefetchRunning.set(false)
            }
        }
    }

    /**
     * Returns cached JSON if still valid for [lat]/[lon], else fetches and updates cache.
     * Blocks the calling worker thread.
     */
    fun getStations(lat: Double, lon: Double): String? {
        cachedOrNull()?.let { c ->
            if (
                c.radiusM >= QUERY_RADIUS_M &&
                distanceMeters(c.lat, c.lon, lat, lon) < REUSE_RADIUS_M
            ) {
                Log.d(TAG, "cache hit full")
                return c.json
            }
        }
        return fetchStationsProgressive(lat, lon, onPartial = null)
    }

    /**
     * Fast nearby (gdebenz `/api/nearby`), then bbox expand (`/api/stations`).
     * [onPartial] may be called on the worker thread (caller should post to UI).
     */
    fun fetchStationsProgressive(
        lat: Double,
        lon: Double,
        onPartial: ((String) -> Unit)?,
    ): String? {
        cachedOrNull()?.let { c ->
            if (
                c.radiusM >= QUERY_RADIUS_M &&
                distanceMeters(c.lat, c.lon, lat, lon) < REUSE_RADIUS_M
            ) {
                onPartial?.invoke(c.json)
                return c.json
            }
        }
        cachedOrNull()?.json?.let { onPartial?.invoke(it) }

        var merged: String? = null
        val fast = runCatching {
            GdebenzStationsClient.fetchNearbyElementsJson(lat, lon, FAST_RADIUS_KM)
                ?.let { FuelStationsJson.sortAndCapByDistance(it, lat, lon) }
        }.onFailure {
            if (it is InterruptedException) {
                Thread.currentThread().interrupt()
                throw it
            }
            Log.w(TAG, "gdebenz nearby: ${it.message}")
        }.getOrNull()
        if (fast != null) {
            merged = fast
            putCache(lat, lon, fast, FAST_RADIUS_M)
            onPartial?.invoke(fast)
            Log.d(TAG, "gdebenz fast≈${FuelStationsJson.elementCount(fast)}")
        }

        val full = runCatching {
            GdebenzStationsClient.fetchBboxElementsJson(lat, lon, QUERY_RADIUS_KM)
                ?.let { FuelStationsJson.sortAndCapByDistance(it, lat, lon) }
        }.onFailure {
            if (it is InterruptedException) {
                Thread.currentThread().interrupt()
                throw it
            }
            Log.w(TAG, "gdebenz full: ${it.message}")
        }.getOrNull()
            ?: if (fast == null) {
                queryFuelStationsLegacy(lat, lon)
            } else {
                null
            }

        if (full != null) {
            merged = FuelStationsJson.mergeById(merged, full, lat, lon)
            putCache(lat, lon, merged!!, QUERY_RADIUS_M)
            onPartial?.invoke(merged)
            Log.d(TAG, "gdebenz full≈${FuelStationsJson.elementCount(merged)}")
            return merged
        }

        return merged ?: cachedOrNull()?.json
    }

    private fun putCache(lat: Double, lon: Double, json: String, radiusM: Int) {
        cache = Cache(
            lat = lat,
            lon = lon,
            json = json,
            fetchedAtEpochMs = System.currentTimeMillis(),
            radiusM = radiusM,
        )
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        return sequenceOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    companion object {
        private const val TAG = "AutologMap"
        private const val CACHE_TTL_MS = 20 * 60_000L
        private const val REUSE_RADIUS_M = 8_000.0
        /** First paint: nearby only (quick). */
        private const val FAST_RADIUS_M = 8_000
        private const val FAST_RADIUS_KM = 8
        /** City + oblast. */
        private const val QUERY_RADIUS_M = 45_000
        private const val QUERY_RADIUS_KM = 45
        private const val HTTP_UA =
            "Autolog/0.8.2 (Android; fuel map; contact=github.com/dyatlovvv-crypto/autolog)"

        private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val out = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, out)
            return out[0].toDouble()
        }

        fun queryFuelStations(lat: Double, lon: Double): String? {
            return try {
                GdebenzStationsClient.fetchElementsJson(lat, lon, QUERY_RADIUS_KM)
                    ?.let { FuelStationsJson.sortAndCapByDistance(it, lat, lon) }
                    ?: queryFuelStationsLegacy(lat, lon)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.d(TAG, "query interrupted (screen closed)")
                null
            } catch (t: Throwable) {
                Log.w(TAG, "query fail: ${t.message}")
                null
            }
        }

        /** Last resort if gdebenz.ru is unavailable. */
        private fun queryFuelStationsLegacy(lat: Double, lon: Double): String? {
            return queryOverpassFuel(lat, lon, QUERY_RADIUS_M, timeoutSec = 14, deadlineSec = 16)
                ?.let { FuelStationsJson.sortAndCapByDistance(it, lat, lon) }
                ?: queryNominatimFuel(lat, lon)
                    ?.let { FuelStationsJson.sortAndCapByDistance(it, lat, lon) }
                ?: queryPhotonFuel(lat, lon)
                    ?.let { FuelStationsJson.sortAndCapByDistance(it, lat, lon) }
        }

        /**
         * Overpass fuel POIs with coordinates.
         * Important: use `out center` / `out;` — NOT `out tags` (tags-only has no lat/lon).
         */
        private fun queryOverpassFuel(
            lat: Double,
            lon: Double,
            aroundM: Int,
            timeoutSec: Int,
            deadlineSec: Int,
        ): String? {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("fuel query cancelled")
            }
            // Nodes first (fast); include ways with center for area-mapped АЗС.
            val q = """
                [out:json][timeout:$timeoutSec];
                (
                  node["amenity"="fuel"](around:$aroundM,$lat,$lon);
                  way["amenity"="fuel"](around:$aroundM,$lat,$lon);
                );
                out center;
            """.trimIndent()
            val endpoints = listOf(
                "https://overpass.openstreetmap.ru/cgi/interpreter",
                "https://lz4.overpass-api.de/api/interpreter",
                "https://overpass-api.de/api/interpreter",
            )
            // Hit RU mirror first (no wait on slow EU), then race backups briefly.
            postOverpass(
                endpoints.first(),
                q,
                connectMs = 3_500,
                readMs = timeoutSec * 1000,
            )?.let { body ->
                if (FuelStationsJson.hasGeoElements(body)) {
                    Log.d(TAG, "overpass RU ok around=$aroundM")
                    return body
                }
            }
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("fuel query cancelled")
            }
            val pool = Executors.newFixedThreadPool(2)
            try {
                val ecs = ExecutorCompletionService<String?>(pool)
                endpoints.drop(1).forEach { ep ->
                    ecs.submit(
                        Callable {
                            postOverpass(ep, q, connectMs = 3_500, readMs = timeoutSec * 1000)
                        },
                    )
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(deadlineSec.toLong())
                repeat(endpoints.size - 1) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException("fuel query cancelled")
                    }
                    val waitNs = (deadline - System.nanoTime()).coerceAtLeast(0)
                    val fut = ecs.poll(waitNs, TimeUnit.NANOSECONDS) ?: return@repeat
                    val body = runCatching { fut.get() }.getOrNull()
                    if (!body.isNullOrBlank() && FuelStationsJson.hasGeoElements(body)) {
                        Log.d(TAG, "overpass ok around=$aroundM bytes=${body.length}")
                        return body
                    }
                }
            } finally {
                pool.shutdownNow()
            }
            return null
        }

        private fun postOverpass(
            endpoint: String,
            query: String,
            connectMs: Int,
            readMs: Int,
        ): String? {
            return try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = connectMs
                    readTimeout = readMs
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8",
                    )
                    setRequestProperty("User-Agent", HTTP_UA)
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.bufferedWriter().use {
                    it.write("data=" + java.net.URLEncoder.encode(query, "UTF-8"))
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
                conn.disconnect()
                if (code in 200..299 && !body.isNullOrBlank()) {
                    Log.d(TAG, "overpass $endpoint ok")
                    body
                } else {
                    Log.w(TAG, "overpass $endpoint -> $code")
                    null
                }
            } catch (t: Throwable) {
                Log.w(TAG, "overpass fail $endpoint: ${t.message}")
                null
            }
        }

        private fun queryNominatimFuel(lat: Double, lon: Double): String? {
            val delta = 0.50
            val south = lat - delta
            val north = lat + delta
            val west = lon - delta / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
            val east = lon + delta / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
            val viewbox = "$west,$north,$east,$south"
            val q = java.net.URLEncoder.encode("[amenity=fuel]", "UTF-8")
            val vb = java.net.URLEncoder.encode(viewbox, "UTF-8")
            val url =
                "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$q" +
                    "&limit=100&bounded=1&viewbox=$vb"
            return try {
                val raw = httpGet(url) ?: return null
                val arr = JSONArray(raw)
                val elements = JSONArray()
                for (i in 0 until arr.length().coerceAtMost(FuelStationsJson.MAX_ELEMENTS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val el = JSONObject()
                    el.put("type", "node")
                    el.put("id", o.optLong("osm_id", i.toLong()))
                    el.put("lat", o.optDouble("lat"))
                    el.put("lon", o.optDouble("lon"))
                    val tags = JSONObject()
                    val name = o.optString("name").ifBlank {
                        o.optString("display_name").substringBefore(',').trim()
                    }
                    if (name.isNotBlank()) tags.put("name", name)
                    el.put("tags", tags)
                    elements.put(el)
                }
                if (elements.length() == 0) return null
                Log.d(TAG, "nominatim fuel=${elements.length()}")
                JSONObject().put("elements", elements).toString()
            } catch (t: Throwable) {
                Log.w(TAG, "nominatim fail: ${t.message}")
                null
            }
        }

        private fun queryPhotonFuel(lat: Double, lon: Double): String? {
            val url =
                "https://photon.komoot.io/api/?q=fuel&lat=$lat&lon=$lon&limit=100&osm_tag=amenity:fuel"
            return try {
                val raw = httpGet(url) ?: return null
                val features = JSONObject(raw).optJSONArray("features") ?: return null
                val elements = JSONArray()
                for (i in 0 until features.length().coerceAtMost(FuelStationsJson.MAX_ELEMENTS)) {
                    val f = features.optJSONObject(i) ?: continue
                    val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue
                    val props = f.optJSONObject("properties")
                    val el = JSONObject()
                    el.put("type", "node")
                    el.put("id", props?.optLong("osm_id", i.toLong()) ?: i.toLong())
                    el.put("lon", coords.optDouble(0))
                    el.put("lat", coords.optDouble(1))
                    val tags = JSONObject()
                    val name = props?.optString("name")?.ifBlank { null }
                        ?: props?.optString("street")?.ifBlank { null }
                        ?: "АЗС"
                    tags.put("name", name)
                    props?.optString("brand")?.takeIf { it.isNotBlank() }?.let { tags.put("brand", it) }
                    el.put("tags", tags)
                    elements.put(el)
                }
                if (elements.length() == 0) return null
                Log.d(TAG, "photon fuel=${elements.length()}")
                JSONObject().put("elements", elements).toString()
            } catch (t: Throwable) {
                Log.w(TAG, "photon fail: ${t.message}")
                null
            }
        }

        private fun httpGet(url: String): String? {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", HTTP_UA)
                setRequestProperty("Accept", "application/json")
            }
            return try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
                if (code in 200..299 && !body.isNullOrBlank()) body else null
            } finally {
                conn.disconnect()
            }
        }
    }
}
