package com.app.sixtyseven

import android.app.Application

class SixtySevenApp : Application() {
    
    lateinit var glassesManager: GlassesManager
        private set
    
    lateinit var apiClient: ApiClient
        private set
    
    lateinit var jobsManager: JobsManager
        private set
    
    lateinit var speaker: Speaker
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        glassesManager = GlassesManager(this)
        apiClient = ApiClient(BuildConfig.API_BASE_URL)
        jobsManager = JobsManager()
        speaker = Speaker(this)
    }
    
    companion object {
        lateinit var instance: SixtySevenApp
            private set
    }
}
