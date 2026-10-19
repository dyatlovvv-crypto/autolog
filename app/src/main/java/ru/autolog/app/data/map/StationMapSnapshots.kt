package ru.autolog.app.data.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * One-shot PNG snapshots for АЗС mini-maps (OSM tiles stitched locally).
 * File is reused — no live map / no re-download on each open.
 */
object StationMapSnapshots {
    private const val TAG = "StationMapSnap"
    private const val UA =
        "Autolog/0.7.8 (Android; station snapshot; contact=github.com/dyatlovvv-crypto/autolog)"
    private const val ZOOM = 14
    private const val OUT_W = 360
    private const val OUT_H = 180
    private const val TILE = 256

    private val inflight = ConcurrentHashMap<String, Any>()

    data class Point(val lat: Double, val lon: Double)

    fun cacheFile(context: Context, lat: Double, lon: Double): File {
        val dir = File(context.applicationContext.filesDir, "station_maps").also { it.mkdirs() }
        return File(dir, "${key(lat, lon)}.png")
    }

    fun existingOrNull(context: Context, lat: Double, lon: Double): File? {
        val f = cacheFile(context, lat, lon)
        return f.takeIf { it.isFile && it.length() > 200L }
    }

    /** Returns cached file or builds once from OSM tiles. */
    suspend fun ensure(context: Context, lat: Double, lon: Double): File? =
        withContext(Dispatchers.IO) {
            if (!lat.isFinite() || !lon.isFinite()) return@withContext null
            val app = context.applicationContext
            existingOrNull(app, lat, lon)?.let { return@withContext it }
            val k = key(lat, lon)
            val lock = inflight.getOrPut(k) { Any() }
            try {
                synchronized(lock) {
                    existingOrNull(app, lat, lon)?.let { return@withContext it }
                    val bmp = stitchMap(lat, lon) ?: return@withContext null
                    val out = cacheFile(app, lat, lon)
                    val tmp = File(out.parentFile, "${out.name}.tmp")
                    FileOutputStream(tmp).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 92, fos)
                    }
                    bmp.recycle()
                    if (tmp.length() > 200L) {
                        if (out.exists()) out.delete()
                        if (tmp.renameTo(out)) {
                            Log.d(TAG, "saved $k bytes=${out.length()}")
                            return@withContext out
                        }
                    }
                    tmp.delete()
                    null
                }
            } finally {
                inflight.remove(k)
            }
        }

    fun prefetchAsync(
        context: Context,
        lat: Double,
        lon: Double,
        onDone: ((File?) -> Unit)? = null,
    ) {
        if (!lat.isFinite() || !lon.isFinite()) {
            onDone?.invoke(null)
            return
        }
        existingOrNull(context, lat, lon)?.let {
            onDone?.invoke(it)
            return
        }
        Thread({
            var result: File? = null
            try {
                val app = context.applicationContext
                result = existingOrNull(app, lat, lon)
                if (result == null) {
                    val bmp = stitchMap(lat, lon)
                    if (bmp != null) {
                        val out = cacheFile(app, lat, lon)
                        val tmp = File(out.parentFile, "${out.name}.tmp")
                        FileOutputStream(tmp).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 92, fos)
                        }
                        bmp.recycle()
                        if (tmp.length() > 200L) {
                            if (out.exists()) out.delete()
                            if (tmp.renameTo(out)) result = out
                        } else {
                            tmp.delete()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "prefetch: ${t.message}")
            } finally {
                onDone?.invoke(result)
            }
        }, "station-map-snap").apply { isDaemon = true }.start()
    }

    /**
     * Resolve WGS84 for a station title (Nominatim). Used to backfill old refuels
     * that only stored the name.
     */
    suspend fun geocode(name: String, near: Point? = null): Point? = withContext(Dispatchers.IO) {
        val q = name.trim()
        if (q.isEmpty()) return@withContext null
        val encoded = URLEncoder.encode(q, "UTF-8")
        val viewbox = near?.let { p ->
            val d = 0.35
            "&viewbox=${p.lon - d},${p.lat + d},${p.lon + d},${p.lat - d}&bounded=0"
        }.orEmpty()
        val url =
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=ru" +
                "&q=$encoded$viewbox"
        try {
            val raw = httpGet(url) ?: return@withContext null
            val arr = JSONArray(raw)
            if (arr.length() == 0) return@withContext null
            val o = arr.getJSONObject(0)
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            if (!lat.isFinite() || !lon.isFinite()) return@withContext null
            Log.d(TAG, "geocode '$q' -> $lat,$lon")
            Point(lat, lon)
        } catch (t: Throwable) {
            Log.w(TAG, "geocode fail: ${t.message}")
            null
        }
    }

    private fun key(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f_%.5f", lat, lon)

    private fun stitchMap(lat: Double, lon: Double): Bitmap? {
        return try {
            val n = 1 shl ZOOM
            val xExact = (lon + 180.0) / 360.0 * n
            val latRad = Math.toRadians(lat)
            val yExact =
                (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
            val centerPxX = xExact * TILE
            val centerPxY = yExact * TILE
            val left = (centerPxX - OUT_W / 2.0)
            val top = (centerPxY - OUT_H / 2.0)
            val x0 = floor(left / TILE).toInt()
            val y0 = floor(top / TILE).toInt()
            val x1 = floor((left + OUT_W - 1) / TILE).toInt()
            val y1 = floor((top + OUT_H - 1) / TILE).toInt()

            val out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.rgb(220, 227, 239))
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    if (tx !in 0 until n || ty !in 0 until n) continue
                    val tileBmp = downloadTile(ZOOM, tx, ty) ?: continue
                    val dstX = (tx * TILE - left).toFloat()
                    val dstY = (ty * TILE - top).toFloat()
                    canvas.drawBitmap(tileBmp, dstX, dstY, null)
                    tileBmp.recycle()
                }
            }
            // Marker at center
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 71, 161)
                style = Paint.Style.FILL
            }
            val cx = OUT_W / 2f
            val cy = OUT_H / 2f
            canvas.drawCircle(cx, cy, 14f, paint)
            paint.color = Color.rgb(66, 165, 245)
            canvas.drawCircle(cx, cy, 8f, paint)
            out
        } catch (t: Throwable) {
            Log.w(TAG, "stitch fail: ${t.message}")
            null
        }
    }

    private fun downloadTile(z: Int, x: Int, y: Int): Bitmap? {
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        return try {
            val bytes = httpBytes(url) ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            Log.w(TAG, "tile $z/$x/$y: ${t.message}")
            null
        }
    }

    private fun httpGet(url: String): String? {
        val bytes = httpBytes(url) ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun httpBytes(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "*/*")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
