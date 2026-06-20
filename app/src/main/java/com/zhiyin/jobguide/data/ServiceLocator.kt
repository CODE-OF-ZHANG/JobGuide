package com.zhiyin.jobguide.data

import android.content.Context

object ServiceLocator {
    @Volatile
    private var repository: JobGuideRepository? = null

    fun repository(context: Context): JobGuideRepository {
        return repository ?: synchronized(this) {
            repository ?: JobGuideRepository(context.applicationContext).also { repository = it }
        }
    }
}
