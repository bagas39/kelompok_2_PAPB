package com.example.cloble

import android.app.Application
import com.example.cloble.di.AppContainer
import com.example.cloble.di.DefaultAppContainer

class CloBleApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
