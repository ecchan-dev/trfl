package org.trfl.app

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

object ImageCaching {
    private var initialized = false
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        Coil.setImageLoader(ImageLoader.Builder(app)
            .memoryCache { MemoryCache.Builder(app).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(File(app.cacheDir, "images")).maxSizeBytes(256L * 1024 * 1024).build() }
            .build())
        initialized = true
    }
}
