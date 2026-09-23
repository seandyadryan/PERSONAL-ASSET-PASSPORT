package com.personalassetpassport.app.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.personalassetpassport.app.PassportApplication
import com.personalassetpassport.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PassportViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PassportApplication
    val repo = app.repository
    val prefs = app.getSharedPreferences("passport-settings", 0)
    val started = MutableStateFlow(prefs.getBoolean("started", false))
    val assets = MutableStateFlow<List<Asset>?>(null)
    val reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val storageFailed = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val email = MutableStateFlow(app.auth.auth?.currentUser?.email)
    private val messages = Channel<String>(Channel.BUFFERED)
    val notices = messages.receiveAsFlow()
    private val authListener = FirebaseAuth.AuthStateListener {
        email.value = it.currentUser?.email
    }

    init {
        app.auth.auth?.addAuthStateListener(authListener)
        viewModelScope.launch {
            try {
                repo.dao.observeAssets().collect { assets.value = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                storageFailed.value = true
            }
        }
        viewModelScope.launch {
            try {
                repo.dao.observeReminders().collect { reminders.value = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                storageFailed.value = true
            }
        }
    }

    fun notice(key: String) {
        messages.trySend(key)
    }

    fun run(onDone: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
                onDone()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notice((e as? PassportException)?.key ?: "generic_error")
            } finally {
                busy.value = false
            }
        }
    }

    fun startLocal() {
        prefs.edit().putBoolean("started", true).apply()
        started.value = true
    }

    fun signIn(activity: Activity) = run { if (app.auth.signIn(activity)) startLocal() }

    fun save(asset: Asset, onDone: () -> Unit) = run(onDone) { repo.save(asset) }

    fun delete(id: String, onDone: () -> Unit) =
        run(onDone) {
            val pending = repo.dao.allReminders().filter { it.assetId == id }
            repo.delete(id)
            pending.forEach { app.scheduler.cancel(it.id) }
        }

    fun attach(id: String, uri: Uri) = run { repo.attach(id, uri) }

    fun photo(uri: Uri, onDone: (String) -> Unit) = run { onDone(repo.importPhoto(uri)) }

    fun addReminder(reminder: Reminder, onDone: () -> Unit) =
        run(onDone) {
            repo.addReminder(reminder)
            try {
                app.scheduler.schedule(reminder)
                notice(if (app.scheduler.allowed()) "saved" else "notification_denied")
            } catch (_: Exception) {
                notice("notification_failed")
            }
        }

    fun removeReminder(id: String) = run {
        repo.dao.deleteReminder(id)
        app.scheduler.cancel(id)
    }

    fun documents(id: String) =
        repo.dao.documents(id).catch {
            notice("storage_failed")
            emit(emptyList())
        }

    fun events(id: String) =
        repo.dao.events(id).catch {
            notice("storage_failed")
            emit(emptyList())
        }

    override fun onCleared() {
        app.auth.auth?.removeAuthStateListener(authListener)
    }
}
