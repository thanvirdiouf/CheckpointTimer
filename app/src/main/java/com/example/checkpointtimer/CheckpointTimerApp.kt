package com.example.checkpointtimer

import android.app.Application
import android.content.Context
import com.example.checkpointtimer.data.AppDatabase
import com.example.checkpointtimer.data.SampleDataSeeder
import com.example.checkpointtimer.data.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework would add
 * more ceremony than it removes.
 */
class AppContainer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val templateRepository = TemplateRepository(AppDatabase.get(context).templateDao())

    private val sampleDataSeeder = SampleDataSeeder(
        repository = templateRepository,
        preferences = context.getSharedPreferences("checkpoint-timer", Context.MODE_PRIVATE),
        scope = scope,
    )

    fun seedSampleData() = sampleDataSeeder.seedIfNeeded()
}

class CheckpointTimerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedSampleData()
    }
}
