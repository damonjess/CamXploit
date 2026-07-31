package com.spyboy.camxploit

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class CamXploitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Nmap binaries and data
        extractNmap(this)
        
        // Initialize OUI Database
        OuiVendorLookup.load(this)
        
        // Initialize Python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Configure Coil
        val imageLoader = ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .respectCacheHeaders(false) // Don't let server cache headers block us
            .build()
        
        Coil.setImageLoader(imageLoader)
    }
}
