package com.example.geonapominalka.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Геокодер на базе Nominatim (OpenStreetMap) — бесплатно, без API-ключа.
 *
 * Штатный Android Geocoder не подходит для поиска "ближайшего" адреса: bounding box
 * в его API — лишь подсказка бэкенду (обычно Google), а не жёсткий фильтр, поэтому
 * "улица Ленина" рядом с пользователем легко подменяется той же улицей в другой стране.
 *
 * Алгоритм здесь честно ищет "от ближнего к дальнему":
 * 1. Пробуем область вокруг пользователя нарастающего размера с bounded=1 —
 *    это ЖЁСТКИЙ фильтр Nominatim: результаты вне области не возвращаются вовсе.
 * 2. Если ни в одной области ничего не нашлось — ищем без ограничения.
 * 3. Среди всех кандидатов на каждом шаге выбираем реально ближайший к пользователю
 *    по расстоянию, а не первый по релевантности из ответа API.
 *
 * Соблюдение политики использования Nominatim (nominatim.org/release-docs/latest/api/Search/):
 * не более ~1 запроса в секунду, обязательный User-Agent — это разовый поиск по
 * нажатию пользователя, укладывается в лимиты личного использования.
 */
object NominatimGeocoder {

    private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
    private const val USER_AGENT = "GeoNapominalka-Android-App"

    data class Result(val latitude: Double, val longitude: Double, val displayName: String)

    suspend fun search(query: String, userLat: Double?, userLon: Double?): Result? =
        withContext(Dispatchers.IO) {
            // Область поиска в градусах: ~30км, ~150км, ~600км — от "своего города" до "своей страны"
            val boxSizesDegrees = listOf(0.3, 1.5, 6.0)

            if (userLat != null && userLon != null) {
                for (box in boxSizesDegrees) {
                    val candidates = request(query, userLat, userLon, box)
                    if (candidates.isNotEmpty()) {
                        return@withContext nearestTo(candidates, userLat, userLon)
                    }
                }
            }

            // Ничего не нашли поблизости — ищем без ограничения области
            val unrestricted = request(query, userLat = null, userLon = null, boxDegrees = null)
            if (unrestricted.isEmpty()) return@withContext null
            if (userLat != null && userLon != null) nearestTo(unrestricted, userLat, userLon) else unrestricted.first()
        }

    private fun nearestTo(candidates: List<Result>, userLat: Double, userLon: Double): Result =
        candidates.minByOrNull { LocationUtils.distanceMeters(userLat, userLon, it.latitude, it.longitude) }!!

    private fun request(query: String, userLat: Double?, userLon: Double?, boxDegrees: Double?): List<Result> {
        val urlBuilder = StringBuilder(BASE_URL)
            .append("?format=json&limit=10&q=")
            .append(URLEncoder.encode(query, "UTF-8"))

        if (boxDegrees != null && userLat != null && userLon != null) {
            val left = userLon - boxDegrees
            val right = userLon + boxDegrees
            val top = userLat + boxDegrees
            val bottom = userLat - boxDegrees
            urlBuilder.append("&viewbox=%s,%s,%s,%s&bounded=1".format(left, top, right, bottom))
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlBuilder.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Result(
                    latitude = obj.getString("lat").toDouble(),
                    longitude = obj.getString("lon").toDouble(),
                    displayName = obj.optString("display_name", query)
                )
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }
}
