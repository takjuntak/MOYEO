package com.neungi.moyeo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MoyeoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}