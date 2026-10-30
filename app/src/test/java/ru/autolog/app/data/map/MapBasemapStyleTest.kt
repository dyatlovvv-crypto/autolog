package ru.autolog.app.data.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBasemapStyleTest {
    @Test
    fun inlinePlanetTiles_replacesUrlWithDatedTiles() {
        val style = """
            {
              "version": 8,
              "sources": {
                "openmaptiles": {
                  "type": "vector",
                  "url": "https://tiles.openfreemap.org/planet"
                }
              },
              "layers": []
            }
        """.trimIndent()
        val tilejson = """
            {
              "tiles": ["https://tiles.openfreemap.org/planet/20261025_pt/{z}/{x}/{y}.pbf"],
              "minzoom": 0,
              "maxzoom": 14,
              "attribution": "© OpenFreeMap"
            }
        """.trimIndent()

        val out = JSONObject(MapBasemapStyle.inlinePlanetTiles(style, tilejson))
        val omt = out.getJSONObject("sources").getJSONObject("openmaptiles")
        assertFalse(omt.has("url"))
        assertEquals(
            "https://tiles.openfreemap.org/planet/20261025_pt/{z}/{x}/{y}.pbf",
            omt.getJSONArray("tiles").getString(0),
        )
        assertEquals(0, omt.getInt("minzoom"))
        assertEquals(14, omt.getInt("maxzoom"))
        assertTrue(omt.getString("attribution").contains("OpenFreeMap"))
    }

    @Test
    fun rasterFallback_isValidJsonWithRasterSource() {
        val root = JSONObject(MapBasemapStyle.rasterCartoJson())
        assertEquals(8, root.getInt("version"))
        val raster = root.getJSONObject("sources").getJSONObject("raster")
        assertEquals("raster", raster.getString("type"))
        assertTrue(raster.getJSONArray("tiles").length() >= 1)
        assertFalse(raster.getJSONArray("tiles").getString(0).contains("leaflet"))
    }
}
