package ru.autolog.app.data.map

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards Overpass `out tags` bug and gdebenz.ru → elements conversion.
 */
class FuelStationsJsonTest {

    @Test
    fun hasGeoElements_rejectsOutTagsWithoutLatLon() {
        val outTags = """
            {"elements":[
              {"type":"node","id":1,"tags":{"amenity":"fuel","name":"Рядом"}},
              {"type":"node","id":2,"tags":{"amenity":"fuel","name":"Ещё"}}
            ]}
        """.trimIndent()
        assertFalse(FuelStationsJson.hasGeoElements(outTags))
        assertNull(FuelStationsJson.sortAndCapByDistance(outTags, 55.75, 37.62))
    }

    @Test
    fun hasGeoElements_acceptsOutCenterAndTopLevelLatLon() {
        val withLat = """
            {"elements":[
              {"type":"node","id":1,"lat":55.75,"lon":37.62,"tags":{"name":"Центр"}}
            ]}
        """.trimIndent()
        val withCenter = """
            {"elements":[
              {"type":"way","id":9,"center":{"lat":55.76,"lon":37.63},"tags":{"name":"Way АЗС"}}
            ]}
        """.trimIndent()
        assertTrue(FuelStationsJson.hasGeoElements(withLat))
        assertTrue(FuelStationsJson.hasGeoElements(withCenter))
    }

    @Test
    fun sortAndCapByDistance_keepsNearestFirst() {
        val userLat = 55.7500
        val userLon = 37.6200
        val body = """
            {"elements":[
              {"type":"node","id":1,"lat":56.50,"lon":38.50,"tags":{"name":"Далеко"}},
              {"type":"node","id":2,"lat":55.751,"lon":37.621,"tags":{"name":"Рядом"}},
              {"type":"node","id":3,"lat":55.80,"lon":37.70,"tags":{"name":"Средне"}}
            ]}
        """.trimIndent()
        val sorted = FuelStationsJson.sortAndCapByDistance(body, userLat, userLon)
        assertNotNull(sorted)
        val els = JSONObject(sorted!!).getJSONArray("elements")
        assertEquals(3, els.length())
        assertEquals("Рядом", els.getJSONObject(0).getJSONObject("tags").getString("name"))
        assertEquals("Средне", els.getJSONObject(1).getJSONObject("tags").getString("name"))
        assertEquals("Далеко", els.getJSONObject(2).getJSONObject("tags").getString("name"))
    }

    @Test
    fun sortAndCapByDistance_flattensCenterToLatLon() {
        val body = """
            {"elements":[
              {"type":"way","id":5,"center":{"lat":55.75,"lon":37.62},"tags":{"name":"Площадь"}}
            ]}
        """.trimIndent()
        val sorted = FuelStationsJson.sortAndCapByDistance(body, 55.75, 37.62)
        assertNotNull(sorted)
        val o = JSONObject(sorted!!).getJSONArray("elements").getJSONObject(0)
        assertEquals(55.75, o.getDouble("lat"), 0.0001)
        assertEquals(37.62, o.getDouble("lon"), 0.0001)
    }

    @Test
    fun fromGdebenzArray_mapsLatLonNameAndKeepsNearest() {
        val arr = JSONArray(
            """
            [
              {"osm_id":"1","name":"Далеко","brand":"X","lat":56.5,"lon":38.5,"addr":"a"},
              {"osm_id":"usr_abc","name":"Лукойл","brand":"Лукойл","lat":55.751,"lon":37.621,"addr":"рядом"},
              {"osm_id":"248014390","brand":"Роснефть","lat":55.80,"lon":37.70}
            ]
            """.trimIndent(),
        )
        val json = FuelStationsJson.fromGdebenzArray(arr)
        assertNotNull(json)
        val sorted = FuelStationsJson.sortAndCapByDistance(json!!, 55.75, 37.62)
        assertNotNull(sorted)
        val first = JSONObject(sorted!!).getJSONArray("elements").getJSONObject(0)
        assertEquals("Лукойл", first.getJSONObject("tags").getString("name"))
        assertEquals(55.751, first.getDouble("lat"), 0.0001)
    }
}
