package com.karelisio.arpege

import android.app.Application
import com.karelisio.arpege.data.AppContainer
import com.karelisio.arpege.data.CrashReporter

class ArpegeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
    }
}
