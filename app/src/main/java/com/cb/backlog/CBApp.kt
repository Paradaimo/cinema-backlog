package com.cb.backlog

import android.app.Application
import com.cb.backlog.di.AppContainer

class CBApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
