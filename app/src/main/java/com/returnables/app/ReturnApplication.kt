package com.returnables.app

import android.app.Application
import com.returnables.app.reminders.DeadlineWorker

class ReturnApplication : Application() {
    override fun onCreate() { super.onCreate(); DeadlineWorker.schedule(this) }
}
