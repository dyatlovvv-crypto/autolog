package ru.autolog.app.data.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Overpass / Nominatim fuel JSON helpers.
 * Overpass must use `out` / `out center` — `out tags` has no lat/lon and must be rejected.
 */
object FuelStationsJson {
    const val MAX_ELEMENTS = 1_200

    fun hasGeoElements(body: String): Boolean {
        return runCatching {
            val els = JSONObject(body).optJSONArray("elements") ?: return false
            for (i in 0 until els.length()) {
                val o = els.optJSONObject(i) ?: continue
                val (la, lo) = latLonOf(o) ?: continue
                if (la.isFinite() && lo.isFinite()) return true
            }
            false
        }.getOrDefault(false)
    }

    /** Nearest-first so nearby АЗС are never dropped when capping. */
    fun sortAndCapByDistance(body: String, lat: Double, lon: Double): String? {
        return runCatching {
            val root = JSONObject(body)
            val els = root.optJSONArray("elements") ?: return null
            data class Row(val dist: Double, val obj: JSONObject)
            val rows = ArrayList<Row>(els.length())
            for (i in 0 until els.length()) {
                val o = els.optJSONObject(i) ?: continue
                val (la, lo) = latLonOf(o) ?: continue
                if (!o.has("lat")) o.put("lat", la)
                if (!o.has("lon")) o.put("lon", lo)
                rows.add(Row(distanceMeters(lat, lon, la, lo), o))
            }
            if (rows.isEmpty()) return null
            rows.sortBy { it.dist }
            val out = JSONArray()
            for (r in rows.take(MAX_ELEMENTS)) out.put(r.obj)
            JSONObject().put("elements", out).toString()
        }.getOrNull()
    }

    fun mergeById(a: String?, b: String, lat: Double, lon: Double): String {
        if (a.isNullOrBlank()) return sortAndCapByDistance(b, lat, lon) ?: b
        return runCatching {
            val elsA = JSONObject(a).optJSONArray("elements") ?: JSONArray()
            val elsB = JSONObject(b).optJSONArray("elements") ?: JSONArray()
            val seen = HashSet<String>()
            val combined = JSONArray()
            fun addAll(arr: JSONArray) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = "${o.optString("type")}:${o.optLong("id")}"
                    if (!seen.add(key)) continue
                    combined.put(o)
                }
            }
            addAll(elsA)
            addAll(elsB)
            sortAndCapByDistance(
                JSONObject().put("elements", combined).toString(),
                lat,
                lon,
            ) ?: b
        }.getOrDefault(b)
    }

    fun elementCount(body: String): Int =
        runCatching { JSONObject(body).optJSONArray("elements")?.length() ?: 0 }.getOrDefault(0)

    /**
     * Convert gdebenz.ru `/api/stations` or `/api/nearby.stations` array into Overpass-shaped JSON.
     */
    fun fromGdebenzArray(arr: JSONArray): String? {
        val elements = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            val osmId = o.optString("osm_id").ifBlank { null } ?: continue
            val el = JSONObject()
            el.put("type", "node")
            el.put("id", osmIdNumeric(osmId, i.toLong()))
            el.put("lat", lat)
            el.put("lon", lon)
            val tags = JSONObject()
            val name = o.optString("name").ifBlank { null }
                ?: o.optString("brand").ifBlank { null }
                ?: "АЗС"
            tags.put("name", name.take(200))
            o.optString("brand").takeIf { it.isNotBlank() }?.let { tags.put("brand", it.take(120)) }
            o.optString("addr").takeIf { it.isNotBlank() }?.let { tags.put("addr", it.take(200)) }
            tags.put("osm_id", osmId)
            el.put("tags", tags)
            elements.put(el)
        }
        if (elements.length() == 0) return null
        return JSONObject().put("elements", elements).toString()
    }

    private fun osmIdNumeric(osmId: String, fallback: Long): Long {
        osmId.toLongOrNull()?.let { return it }
        val digits = osmId.filter { it.isDigit() }
        digits.toLongOrNull()?.let { return it }
        return osmId.hashCode().toLong().and(0x7fff_ffff_ffff_ffffL).takeIf { it != 0L } ?: fallback
    }

    private fun latLonOf(o: JSONObject): Pair<Double, Double>? {
        val la = when {
            o.has("lat") && !o.isNull("lat") -> o.optDouble("lat")
            else -> o.optJSONObject("center")?.optDouble("lat")
        } ?: return null
        val lo = when {
            o.has("lon") && !o.isNull("lon") -> o.optDouble("lon")
            else -> o.optJSONObject("center")?.optDouble("lon")
        } ?: return null
        if (!la.isFinite() || !lo.isFinite()) return null
        return la to lo
    }

    /** Haversine (JVM-safe, no Android Location). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) +
            cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
