package com.weatherclone.app

import android.app.Application
import com.weatherclone.app.background.Notifier
import com.weatherclone.app.background.Sync

class StratusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Sync.schedule(this)
    }
}
