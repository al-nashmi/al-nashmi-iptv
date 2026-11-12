package com.mytv.player

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        FamilyFilter.init(this)
        // Restore the account here so any screen works after the system restarts the process.
        Repo.account = Prefs.load()
    }
}
