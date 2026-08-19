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
 * готовые растровые стили, и ни один из них номера домов не показывает вообще
 * (упрощённые базовые карты для приложений так устроены). Пробовали зеркало
 * классического OSM Standard на серверах Wikimedia (maps.wikimedia.org) — оно тоже
 * оказалось нестабильным (тайлы не грузятся), поэтому убрано из списка стилей.
 * Если понадобятся номера домов — единственный практичный вариант это платный
 * тайл-провайдер с ключом (Mapbox/MapTiler/Stadia Maps — у всех есть щедрый бесплатный
 * тариф без карты, в отличие от Google).
 */
object TileSources {

    /** Минималистичная светлая схема CARTO Positron — быстрая, без номеров домов. */
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
        VOYAGER(cartoVoyager),
        SATELLITE(esriSatellite),
        HYBRID(esriSatellite, isHybrid = true);

        companion object {
            fun fromIndex(index: Int): MapStyle = entries.getOrElse(index) { LIGHT }
        }
    }
}
