package ru.autolog.app.data.map

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject
import org.maplibre.android.maps.Style

/**
 * MapLibre basemap for Autolog.
 *
 * OpenFreeMap liberty style points `openmaptiles` at TileJSON
 * (`https://tiles.openfreemap.org/planet`). If the SDK does not resolve that
 * TileJSON and requests `…/planet/{z}/{x}/{y}.pbf`, OFM returns **empty**
 * tiles (`empty tile wildcard`) → blank beige map with markers still drawn.
 *
 * We fetch TileJSON ourselves and inline absolute `tiles[]` into the style.
 * On any failure → neutral Carto raster (OSM data, text attribution only).
 */
object MapBasemapStyle {
    private const val TAG = "AutologMapStyle"
    private const val LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    private const val PLANET_TILEJSON = "https://tiles.openfreemap.org/planet"

    private val io by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "autolog-map-style").apply { isDaemon = true }
        }
    }

    /** Async: OpenFreeMap (inlined) or raster fallback. Callback on main thread. */
    fun prepare(onReady: (Style.Builder) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        io.execute {
            val builder = runCatching {
                Style.Builder().fromJson(openFreeMapLibertyInlined())
            }.recoverCatching { e ->
                Log.w(TAG, "OpenFreeMap style failed, raster fallback", e)
                Style.Builder().fromJson(rasterCartoJson())
            }.getOrElse {
                Style.Builder().fromJson(rasterCartoJson())
            }
            main.post { onReady(builder) }
        }
    }

    /** Testable: splice TileJSON tiles into liberty-like style JSON. */
    fun inlinePlanetTiles(styleJson: String, tileJson: String): String {
        val root = JSONObject(styleJson)
        val tile = JSONObject(tileJson)
        val sources = root.getJSONObject("sources")
        val omt = sources.optJSONObject("openmaptiles")
            ?: error("style missing openmaptiles source")
        val tiles = tile.optJSONArray("tiles")
            ?: error("tilejson missing tiles")
        if (tiles.length() == 0) error("tilejson tiles empty")
        omt.remove("url")
        omt.put("type", "vector")
        omt.put("tiles", tiles)
        if (tile.has("minzoom")) omt.put("minzoom", tile.getInt("minzoom"))
        if (tile.has("maxzoom")) omt.put("maxzoom", tile.getInt("maxzoom"))
        if (tile.has("bounds")) omt.put("bounds", tile.getJSONArray("bounds"))
        if (tile.has("attribution")) {
            omt.put("attribution", tile.getString("attribution"))
        }
        return root.toString()
    }

    fun rasterCartoJson(): String = """
        {
          "version": 8,
          "name": "Autolog raster",
          "sources": {
            "raster": {
              "type": "raster",
              "tiles": [
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
                "https://b.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
                "https://c.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
              ],
              "tileSize": 256,
              "attribution": "© OpenStreetMap © CARTO"
            }
          },
          "layers": [
            {
              "id": "raster",
              "type": "raster",
              "source": "raster",
              "minzoom": 0,
              "maxzoom": 20
            }
          ]
        }
    """.trimIndent()

    private fun openFreeMapLibertyInlined(): String {
        val style = httpGet(LIBERTY_STYLE)
        val planet = httpGet(PLANET_TILEJSON)
        return inlinePlanetTiles(style, planet)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Autolog/${ru.autolog.app.BuildConfig.APP_VERSION}")
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) {
                error("GET $url -> HTTP $code")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
