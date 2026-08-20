package com.example.geonapominalka.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Подсчёт текущего размера дискового кэша тайлов карты — только для отладки/визуального
 * контроля (см. экран "Консоль" и мелкую подпись на карте). Не влияет на саму логику
 * кэширования, OSMDroid сам решает, что и когда вычищать по лимитам из Configuration.
 */
object TileCacheInfo {

    suspend fun currentSizeMb(): Double = withContext(Dispatchers.IO) {
        val dir = Configuration.getInstance().osmdroidTileCache ?: return@withContext 0.0
        dirSizeBytes(dir) / 1024.0 / 1024.0
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            total += if (f.isDirectory) dirSizeBytes(f) else f.length()
        }
        return total
    }
}
