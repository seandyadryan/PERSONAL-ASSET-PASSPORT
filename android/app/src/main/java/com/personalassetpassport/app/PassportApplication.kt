package com.personalassetpassport.app

import android.app.Application
import com.personalassetpassport.app.auth.GoogleAuth
import com.personalassetpassport.app.backup.BackupService
import com.personalassetpassport.app.data.PassportDatabase
import com.personalassetpassport.app.data.PassportRepository
import com.personalassetpassport.app.notifications.ReminderScheduler

class PassportApplication : Application() {
    val database by lazy { PassportDatabase.open(this) }
    val repository by lazy { PassportRepository(this, database) }
    val auth by lazy { GoogleAuth(this) }
    val scheduler by lazy { ReminderScheduler(this) }
    val backup by lazy { BackupService(repository) }
}
