package com.apex.mapboxlab

import com.mapbox.geojson.Point
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** User-submitted address search. Temporary results are not persisted or logged. */
class PlaceSearch(private val publicToken: String) : AutoCloseable {
    data class Place(val label: String, val point: Point)
    private val executor = Executors.newSingleThreadExecutor()
    private val epochs = RequestEpoch()
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { epochs.next(); connection?.disconnect() }
    fun search(query: String, callback: (Result<List<Place>>) -> Unit) {
        cancel()
        val epoch = epochs.next()
        executor.execute {
            val result = runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val token = URLEncoder.encode(publicToken, "UTF-8")
                val conn = URL("https://api.mapbox.com/search/geocode/v6/forward?q=$encoded&autocomplete=false&limit=5&country=us&access_token=$token").openConnection() as HttpURLConnection
                connection = conn
                conn.connectTimeout = 12000; conn.readTimeout = 12000
                try {
                    check(conn.responseCode == 200) { "Address search failed" }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val features = JSONObject(body).getJSONArray("features")
                    (0 until features.length()).map { i ->
                        val feature = features.getJSONObject(i)
                        val properties = feature.getJSONObject("properties")
                        val coordinate = feature.getJSONObject("geometry").getJSONArray("coordinates")
                        Place(properties.optString("full_address", properties.optString("name", "Map point")), Point.fromLngLat(coordinate.getDouble(0), coordinate.getDouble(1)))
                    }
                } finally { conn.disconnect(); if (connection === conn) connection = null }
            }
            if (epochs.current(epoch)) callback(result)
        }
    }
    override fun close() { cancel(); executor.shutdownNow() }
}
