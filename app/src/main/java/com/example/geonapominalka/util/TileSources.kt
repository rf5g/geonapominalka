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
 * User-Agent.
 *
 * У CARTO нет отдельных переключаемых слоёв (типа "номера домов вкл/выкл") — это
 * готовые растровые стили. "Light" (Positron) — минималистичный, без номеров домов.
 * Чтобы получить детализацию как в классическом OSM (в т.ч. номера домов на крупном
 * зуме), используется зеркало официального стиля "OSM Standard" на серверах
 * Викимедиа (maps.wikimedia.org) — тоже бесплатно, без ключа, и без блокировки
 * мобильных приложений, в отличие от tile.openstreetmap.org.
 */
object TileSources {

    /** Минималистичная светлая схема CARTO Positron — быстрая, но без номеров домов. */
    val cartoLight: ITileSource = XYTileSource(
        "CartoLight", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/",
            "https://d.basemaps.cartocdn.com/light_all/"
        )
    )

    /** Более "цветной" и подробный стиль CARTO Voyager (больше POI, но тоже без номеров домов). */
    val cartoVoyager: ITileSource = XYTileSource(
        "CartoVoyager", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    )

    /** Классический стиль OSM Standard (номера домов видны при увеличении), зеркало Wikimedia. */
    val osmStandard: ITileSource = XYTileSource(
        "WikimediaOsm", 0, 19, 256, ".png",
        arrayOf("https://maps.wikimedia.org/osm-intl/")
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

    /**
     * Полупрозрачный слой ТОЛЬКО с подписями (без заливки) от CARTO — накладывается
     * поверх спутникового снимка, чтобы получить "гибридный" режим (снимок + названия/дороги).
     */
    val labelsOverlay: ITileSource = XYTileSource(
        "CartoLabelsOnly", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_only_labels/",
            "https://b.basemaps.cartocdn.com/light_only_labels/",
            "https://c.basemaps.cartocdn.com/light_only_labels/",
            "https://d.basemaps.cartocdn.com/light_only_labels/"
        )
    )

    /** Порядок пунктов в переключателе на карте и в настройках — должен совпадать в обоих местах. */
    enum class MapStyle(val tileSource: ITileSource, val isHybrid: Boolean = false) {
        LIGHT(cartoLight),
        DETAILED(osmStandard),
        VOYAGER(cartoVoyager),
        SATELLITE(esriSatellite),
        HYBRID(esriSatellite, isHybrid = true);

        companion object {
            fun fromIndex(index: Int): MapStyle = entries.getOrElse(index) { LIGHT }
        }
    }
}
