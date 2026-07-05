package com.mitv.master

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MitvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
