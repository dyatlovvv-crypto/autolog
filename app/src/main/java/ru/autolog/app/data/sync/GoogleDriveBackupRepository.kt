package ru.autolog.app.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import ru.autolog.app.BuildConfig

data class GoogleAccountInfo(
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)

data class CloudBackupMeta(
    val fileId: String,
    val modifiedTimeEpochMs: Long?,
    val sizeBytes: Long?,
)

/**
 * Google Sign-In + Drive App Data snapshots.
 * Linking / signing in never touches the local Room DB.
 */
class GoogleDriveBackupRepository(
    private val appContext: Context,
) : CloudBackupGateway {
    override fun isOAuthConfigured(): Boolean = true

    /** Optional Web client ID improves ID token; Drive scopes work with Android client alone. */
    fun hasWebClientId(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun signInClient(): GoogleSignInClient {
        // Drive App Data needs email + scope only. requestIdToken(webClientId) often
        // causes ApiException 10 (DEVELOPER_ERROR) when the Web client is mislinked.
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(appContext, options)
    }

    override fun signInIntent(): Intent = signInClient().signInIntent

    override fun lastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(appContext)

    override fun accountInfo(account: GoogleSignInAccount?): GoogleAccountInfo? {
        val acc = account ?: lastSignedInAccount() ?: return null
        val email = acc.email ?: return null
        return GoogleAccountInfo(
            email = email,
            displayName = acc.displayName,
            photoUrl = acc.photoUrl?.toString(),
        )
    }

    override suspend fun completeSignIn(data: Intent?): Result<GoogleAccountInfo> = runCatching {
        if (data == null) error("Вход отменён")
        val taskAccount = try {
            GoogleSignIn.getSignedInAccountFromIntent(data).await()
        } catch (e: Exception) {
            val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
            when (code) {
                10 -> error(
                    "Вход в Google недоступен (ошибка настройки OAuth, код 10). " +
                        "Проверьте SHA-1 и package ru.autolog.app в Google Cloud.",
                )
                12501 -> error("Вход отменён")
                else -> error(
                    "Не удалось войти в Google" +
                        (if (code != null) " (код $code)" else "") +
                        ". Попробуйте ещё раз.",
                )
            }
        }
        accountInfo(taskAccount) ?: error("Не удалось получить данные аккаунта Google")
    }

    override suspend fun signOut() {
        runCatching { signInClient().signOut().await() }
        runCatching { signInClient().revokeAccess().await() }
    }

    override suspend fun findBackupMeta(): CloudBackupMeta? = withContext(Dispatchers.IO) {
        val drive = driveService() ?: return@withContext null
        val result = drive.files().list()
            .setSpaces(APP_DATA)
            .setQ("name = '$BACKUP_NAME' and trashed = false")
            .setFields("files(id, modifiedTime, size)")
            .setPageSize(1)
            .execute()
        val file = result.files?.firstOrNull() ?: return@withContext null
        CloudBackupMeta(
            fileId = file.id,
            modifiedTimeEpochMs = file.modifiedTime?.value,
            sizeBytes = file.getSize(),
        )
    }

    fun hasDriveAppDataScope(): Boolean {
        val account = lastSignedInAccount() ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))
    }

    override suspend fun uploadSnapshot(json: String): Result<CloudBackupMeta> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasDriveAppDataScope()) {
                error("Нужно снова разрешить доступ Google. Отвяжите аккаунт и привяжите заново.")
            }
            val drive = driveService() ?: error("Сначала привяжите Google-аккаунт")
            val content = ByteArrayContent.fromString("application/json", json)
            val existing = findBackupMeta()
            val file = if (existing != null) {
                drive.files().update(existing.fileId, File(), content)
                    .setFields("id, modifiedTime, size")
                    .execute()
            } else {
                val meta = File().apply {
                    name = BACKUP_NAME
                    parents = listOf(APP_DATA)
                }
                drive.files().create(meta, content)
                    .setFields("id, modifiedTime, size")
                    .execute()
            }
            CloudBackupMeta(
                fileId = file.id,
                modifiedTimeEpochMs = file.modifiedTime?.value,
                sizeBytes = file.getSize(),
            )
        }.onFailure { e ->
            Log.e(TAG, "uploadSnapshot failed", e)
        }.mapFailure { e ->
            if (e is UserRecoverableAuthIOException) {
                Exception(
                    "Нужно снова разрешить доступ Google. Отвяжите аккаунт и привяжите заново.",
                    e,
                )
            } else {
                e
            }
        }
    }

    private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
        exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this


    override suspend fun downloadSnapshot(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = driveService() ?: error("Сначала привяжите Google-аккаунт")
            val meta = findBackupMeta() ?: error("В Google пока нет резервной копии")
            val out = ByteArrayOutputStream()
            drive.files().get(meta.fileId).executeMediaAndDownloadTo(out)
            out.toString(Charsets.UTF_8.name())
        }
    }

    private fun driveService(): Drive? {
        val signedIn = lastSignedInAccount() ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            appContext,
            listOf(DriveScopes.DRIVE_APPDATA),
        ).apply {
            val acct = signedIn.account
            if (acct != null) {
                selectedAccount = acct
            } else {
                selectedAccountName = signedIn.email ?: return null
            }
        }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("Autolog").build()
    }

    companion object {
        private const val TAG = "AutologDrive"
        private const val APP_DATA = "appDataFolder"
        private const val BACKUP_NAME = "autolog_backup.json"
    }
}
