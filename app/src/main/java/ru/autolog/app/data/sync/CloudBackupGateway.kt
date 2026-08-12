package ru.autolog.app.data.sync

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * Drive App Data backup port. Real impl talks to Google; tests inject a fake.
 */
interface CloudBackupGateway {
    fun isOAuthConfigured(): Boolean
    fun signInIntent(): Intent
    fun lastSignedInAccount(): GoogleSignInAccount?
    fun accountInfo(account: GoogleSignInAccount? = lastSignedInAccount()): GoogleAccountInfo?
    suspend fun completeSignIn(data: Intent?): Result<GoogleAccountInfo>
    suspend fun signOut()
    suspend fun findBackupMeta(): CloudBackupMeta?
    suspend fun uploadSnapshot(json: String): Result<CloudBackupMeta>
    suspend fun downloadSnapshot(): Result<String>
}
