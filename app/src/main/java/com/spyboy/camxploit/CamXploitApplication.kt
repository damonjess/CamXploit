package com.spyboy.camxploit

import android.app.Application
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
    }
}