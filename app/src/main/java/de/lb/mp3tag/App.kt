package de.lb.mp3tag

import android.app.Application
import de.lb.mp3tag.di.AppContainer

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
