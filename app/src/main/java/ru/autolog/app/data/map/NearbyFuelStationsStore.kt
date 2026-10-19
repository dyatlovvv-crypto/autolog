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
 * Prefetches nearby fuel stations (Overpass + fallbacks) so the map opens with data ready.
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
    )

    fun cachedOrNull(maxAgeMs: Long = CACHE_TTL_MS): Cache? {
        val c = cache ?: return null
        if (System.currentTimeMillis() - c.fetchedAtEpochMs > maxAgeMs) return null
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
                val json = queryFuelStations(loc.latitude, loc.longitude) ?: return@execute
                cache = Cache(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    json = json,
                    fetchedAtEpochMs = System.currentTimeMillis(),
                )
                Log.d(TAG, "prefetch ok lat=${loc.latitude} lon=${loc.longitude}")
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
            if (distanceMeters(c.lat, c.lon, lat, lon) < REUSE_RADIUS_M) {
                Log.d(TAG, "cache hit")
                return c.json
            }
        }
        // Stale-but-present cache: show immediately while a refresh runs (caller may ignore).
        val stale = cache
        val json = queryFuelStations(lat, lon)
        if (json != null) {
            cache = Cache(lat, lon, json, System.currentTimeMillis())
            return json
        }
        return stale?.json ?: cachedOrNull(maxAgeMs = CACHE_TTL_MS * 2)?.json
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
        private const val REUSE_RADIUS_M = 2_500.0
        private const val HTTP_UA =
            "Autolog/0.7.8 (Android; fuel map; contact=github.com/dyatlovvv-crypto/autolog)"
        private const val MAX_ELEMENTS = 80

        private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val out = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, out)
            return out[0].toDouble()
        }

        fun queryFuelStations(lat: Double, lon: Double): String? {
            return try {
                queryFuelStationsInner(lat, lon)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.d(TAG, "query interrupted (screen closed)")
                null
            } catch (t: Throwable) {
                Log.w(TAG, "query fail: ${t.message}")
                null
            }
        }

        private fun queryFuelStationsInner(lat: Double, lon: Double): String? {
            val aroundM = 5_000
            // Nodes-first: much faster than full nwr on busy Overpass mirrors.
            val q = """
                [out:json][timeout:12];
                (
                  node["amenity"="fuel"](around:$aroundM,$lat,$lon);
                  way["amenity"="fuel"](around:$aroundM,$lat,$lon);
                );
                out center tags;
            """.trimIndent()
            val endpoints = listOf(
                "https://overpass.openstreetmap.ru/cgi/interpreter",
                "https://lz4.overpass-api.de/api/interpreter",
                "https://overpass.osm.ch/api/interpreter",
                "https://overpass-api.de/api/interpreter",
            )
            val pool = Executors.newFixedThreadPool(endpoints.size.coerceAtMost(3))
            try {
                val ecs = ExecutorCompletionService<String?>(pool)
                endpoints.forEach { ep ->
                    ecs.submit(Callable { postOverpass(ep, q) })
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(14)
                for (i in endpoints.indices) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException("fuel query cancelled")
                    }
                    val waitNs = (deadline - System.nanoTime()).coerceAtLeast(0)
                    val fut = ecs.poll(waitNs, TimeUnit.NANOSECONDS) ?: break
                    val body = runCatching { fut.get() }.getOrNull()
                    val trimmed = body?.let { trimElements(it) }
                    if (!trimmed.isNullOrBlank() && hasFuelElements(trimmed)) {
                        Log.d(TAG, "overpass ok, bytes=${trimmed.length}")
                        return trimmed
                    }
                }
            } finally {
                pool.shutdownNow()
            }
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("fuel query cancelled")
            }
            Log.w(TAG, "overpass all failed, trying fallbacks")
            return queryNominatimFuel(lat, lon) ?: queryPhotonFuel(lat, lon)
        }

        private fun trimElements(body: String): String? {
            return runCatching {
                val root = JSONObject(body)
                val els = root.optJSONArray("elements") ?: return body
                if (els.length() <= MAX_ELEMENTS) return body
                val trimmed = JSONArray()
                for (i in 0 until MAX_ELEMENTS) trimmed.put(els.get(i))
                root.put("elements", trimmed).toString()
            }.getOrNull()
        }

        private fun hasFuelElements(body: String): Boolean {
            return runCatching {
                JSONObject(body).optJSONArray("elements")?.length()?.let { it > 0 } == true
            }.getOrDefault(false)
        }

        private fun postOverpass(endpoint: String, query: String): String? {
            return try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6_000
                    readTimeout = 12_000
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
            val delta = 0.06
            val south = lat - delta
            val north = lat + delta
            val west = lon - delta / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
            val east = lon + delta / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
            val viewbox = "$west,$north,$east,$south"
            val q = java.net.URLEncoder.encode("[amenity=fuel]", "UTF-8")
            val vb = java.net.URLEncoder.encode(viewbox, "UTF-8")
            val url =
                "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$q" +
                    "&limit=50&bounded=1&viewbox=$vb"
            return try {
                val raw = httpGet(url) ?: return null
                val arr = JSONArray(raw)
                val elements = JSONArray()
                for (i in 0 until arr.length().coerceAtMost(MAX_ELEMENTS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val el = JSONObject()
                    el.put("type", "node")
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
                "https://photon.komoot.io/api/?q=fuel&lat=$lat&lon=$lon&limit=50&osm_tag=amenity:fuel"
            return try {
                val raw = httpGet(url) ?: return null
                val features = JSONObject(raw).optJSONArray("features") ?: return null
                val elements = JSONArray()
                for (i in 0 until features.length().coerceAtMost(MAX_ELEMENTS)) {
                    val f = features.optJSONObject(i) ?: continue
                    val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue
                    val props = f.optJSONObject("properties")
                    val el = JSONObject()
                    el.put("type", "node")
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
                connectTimeout = 8_000
                readTimeout = 10_000
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
