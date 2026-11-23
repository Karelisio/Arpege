package com.karelisio.arpege

import android.app.Application
import com.karelisio.arpege.data.AppContainer

class ArpegeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
