package com.puretv.store

import android.app.Application
import com.puretv.store.data.Notifications

class PureTVStoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
