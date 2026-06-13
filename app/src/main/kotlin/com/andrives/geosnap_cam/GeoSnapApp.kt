package com.andrives.geosnap_cam

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GeoSnapApp : Application() {
    companion object {
        init {
            System.loadLibrary("geosnap_native")
        }
    }
}
