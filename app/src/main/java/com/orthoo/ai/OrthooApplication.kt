package com.orthoo.ai

import android.app.Application
import com.orthoo.ai.data.db.OrthooDatabase
import com.orthoo.ai.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OrthooApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            OrthooDatabase.seedInitialData(container.database)
        }
    }

    override fun onTerminate() {
        container.speechManager.shutdown()
        super.onTerminate()
    }
}
