package com.zhiyin.jobguide.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.jobGuideDataStore by preferencesDataStore(name = "jobguide_store")

class JobGuideRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.jobGuideDataStore

    val config: Flow<AppConfig> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> AppConfig.fromJson(prefs[Keys.config] ?: AppConfig().toJson()) }

    val stats: Flow<RunStats> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> RunStats.fromJson(prefs[Keys.stats] ?: RunStats().toJson()) }

    val logs: Flow<List<RunLog>> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> parseLogs(prefs[Keys.logs] ?: "[]").sortedByDescending { it.timestamp }.take(300) }

    val records: Flow<List<JobRecord>> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> parseRecords(prefs[Keys.records] ?: "[]").sortedByDescending { it.timestamp }.take(800) }

    val command: Flow<AutomationCommand> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> AutomationCommand.fromJson(prefs[Keys.command] ?: AutomationCommand().toJson()) }

    suspend fun updateConfig(transform: (AppConfig) -> AppConfig) {
        dataStore.edit { prefs ->
            val current = AppConfig.fromJson(prefs[Keys.config] ?: AppConfig().toJson())
            prefs[Keys.config] = transform(current).toJson()
        }
    }

    suspend fun replaceConfig(config: AppConfig) {
        dataStore.edit { prefs -> prefs[Keys.config] = config.toJson() }
        appendLog(RunLog(level = LogLevel.Success, message = "已导入 JSON 配置"))
    }

    suspend fun importConfigJson(raw: String) {
        replaceConfig(AppConfig.fromJson(raw))
    }

    suspend fun exportConfigJson(): String = config.first().toJson()

    suspend fun issueCommand(type: AutomationCommandType) {
        if (type == AutomationCommandType.Start) {
            dataStore.edit { prefs ->
                val current = RunStats.fromJson(prefs[Keys.stats] ?: RunStats().toJson())
                if (current.status != TaskStatus.Paused && current.status != TaskStatus.Running) {
                    prefs[Keys.stats] = RunStats(
                        status = TaskStatus.Running,
                        currentStep = "准备启动招聘平台",
                        startedAt = System.currentTimeMillis()
                    ).toJson()
                }
                prefs[Keys.command] = AutomationCommand(type = type).toJson()
            }
        } else {
            dataStore.edit { prefs -> prefs[Keys.command] = AutomationCommand(type = type).toJson() }
        }
    }

    suspend fun updateStats(transform: (RunStats) -> RunStats) {
        dataStore.edit { prefs ->
            val current = RunStats.fromJson(prefs[Keys.stats] ?: RunStats().toJson())
            prefs[Keys.stats] = transform(current).toJson()
        }
    }

    suspend fun appendLog(log: RunLog) {
        dataStore.edit { prefs ->
            val current = parseLogs(prefs[Keys.logs] ?: "[]")
            prefs[Keys.logs] = (current + log).takeLast(300).toLogJson()
        }
    }

    suspend fun addRecord(record: JobRecord) {
        dataStore.edit { prefs ->
            val current = parseRecords(prefs[Keys.records] ?: "[]")
            val allFingerprints = (record.aliases + record.fingerprint).toSet()
            val deduped = (current.filterNot { existing ->
                existing.platform == record.platform && allFingerprints.any { existing.matchesFingerprint(it) }
            } + record).takeLast(800)
            prefs[Keys.records] = deduped.toRecordJson()
        }
    }

    suspend fun clearTodayStats() {
        dataStore.edit { prefs -> prefs[Keys.stats] = RunStats().toJson() }
        appendLog(RunLog(level = LogLevel.Warning, message = "已清空今日统计"))
    }

    suspend fun clearRecords() {
        dataStore.edit { prefs -> prefs[Keys.records] = "[]" }
        appendLog(RunLog(level = LogLevel.Warning, message = "已清空岗位记录"))
    }

    suspend fun clearLogs() {
        dataStore.edit { prefs -> prefs[Keys.logs] = "[]" }
    }

    val accessibilityWasEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.accessibilityWasEnabled] ?: false }

    suspend fun setAccessibilityWasEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.accessibilityWasEnabled] = enabled }
    }

    private object Keys {
        val config = stringPreferencesKey("config_json")
        val stats = stringPreferencesKey("stats_json")
        val logs = stringPreferencesKey("logs_json")
        val records = stringPreferencesKey("records_json")
        val command = stringPreferencesKey("command_json")
        val accessibilityWasEnabled = booleanPreferencesKey("accessibility_was_enabled")
    }
}
