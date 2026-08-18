package com.example.geonapominalka.util

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Бесплатные тайл-источники без API-ключа.
 *
 * ВАЖНО: TileSourceFactory.MAPNIK (штатный источник osmdroid) бьёт напрямую в
 * tile.openstreetmap.org — этот сервер официально не предназначен для встраивания
 * в приложения и активно блокирует такие запросы (403 Forbidden), даже с корректным
 * User-Agent. Поэтому в качестве обычной схемы используется CARTO Basemaps —
 * тоже бесплатно и без ключа, но без такой блокировки при разумном объёме запросов.
 */
object TileSources {

    val cartoLight: ITileSource = XYTileSource(
        "CartoLight", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/",
            "https://d.basemaps.cartocdn.com/light_all/"
        )
    )

    val esriSatellite: ITileSource = object : XYTileSource(
        "EsriWorldImagery", 0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$y/$x"
        }
    }

    val all: List<ITileSource> = listOf(cartoLight, esriSatellite)
}
