package com.example.financewidget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.*
import java.util.concurrent.TimeUnit

class FinanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FinanceGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleEcoPeriodicWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "FinanceEcoSyncWork"

        fun scheduleEcoPeriodicWork(context: Context) {
            // 배터리 부족 상태가 아니고 인터넷이 연결되어 있을 때만 동작
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<FinanceSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            // 홈 화면에 위젯을 올리는 순간 즉시 1회 실시간 데이터 호출
            val immediateRequest = OneTimeWorkRequestBuilder<FinanceSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(immediateRequest)
        }
    }
}

class FinanceSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val payload = FinanceRepository.fetchAllMarketData()
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(FinanceGlanceWidget::class.java)

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetKeys.PAYLOAD_JSON] = payload.toJsonString()
                    }
                }
                FinanceGlanceWidget().update(appContext, glanceId)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}