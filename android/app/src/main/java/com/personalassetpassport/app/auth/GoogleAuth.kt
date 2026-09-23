package com.personalassetpassport.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.personalassetpassport.app.data.PassportException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleAuth(private val context: Context) {
    private val app =
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    val auth: FirebaseAuth? = app?.let { FirebaseAuth.getInstance(it) }
    private val clientId: String? =
        context.resources
            .getIdentifier("default_web_client_id", "string", context.packageName)
            .takeIf { it != 0 }
            ?.let(context::getString)
    val configured
        get() = auth != null && !clientId.isNullOrBlank()

    suspend fun signIn(activity: Activity): Boolean {
        if (!configured) throw PassportException("firebase_missing")
        try {
            val option = GetSignInWithGoogleOption.Builder(clientId!!).build()
            val response =
                CredentialManager.create(activity)
                    .getCredential(
                        activity,
                        GetCredentialRequest.Builder().addCredentialOption(option).build(),
                    )
            val credential = response.credential
            if (
                credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            )
                throw PassportException("login_failed")
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth!!.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            return true
        } catch (_: GetCredentialCancellationException) {
            return false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw PassportException("login_failed")
        }
    }

    suspend fun signOut() {
        auth?.signOut()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    suspend fun verifyServer(baseUrl: String) {
        val token =
            auth?.currentUser?.getIdToken(false)?.await()?.token
                ?: throw PassportException("login_failed")
        withContext(Dispatchers.IO) {
            val url = runCatching { URL(baseUrl.trim()) }.getOrNull()
            if (
                url == null || url.protocol != "https" || url.host.isBlank() || url.userInfo != null
            )
                throw PassportException("https_required")
            val connection = URL(url, "/api/me").openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (connection.responseCode != 200) throw PassportException("api_failed")
            } finally {
                connection.disconnect()
            }
        }
    }
}
