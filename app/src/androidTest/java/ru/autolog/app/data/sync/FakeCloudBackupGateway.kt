package ru.autolog.app.data.sync

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * Deterministic Drive stand-in for instrumented UI tests.
 * Never talks to Google — injects upload/download failures (quota, network, etc.).
 */
class FakeCloudBackupGateway(
    private val uploadFailure: Throwable? = null,
    private val downloadFailure: Throwable? = null,
    private val meta: CloudBackupMeta? = null,
    private val account: GoogleAccountInfo? = GoogleAccountInfo(
        email = "uitest-cloud@example.com",
        displayName = "UITest Cloud",
        photoUrl = null,
    ),
) : CloudBackupGateway {
    @Volatile
    var uploadCalls: Int = 0
        private set

    override fun isOAuthConfigured(): Boolean = true

    override fun signInIntent(): Intent = Intent("ru.autolog.app.TEST_SIGN_IN")

    override fun lastSignedInAccount(): GoogleSignInAccount? = null

    override fun accountInfo(account: GoogleSignInAccount?): GoogleAccountInfo? = this.account

    override suspend fun completeSignIn(data: Intent?): Result<GoogleAccountInfo> =
        Result.success(requireNotNull(account))

    override suspend fun signOut() = Unit

    override suspend fun findBackupMeta(): CloudBackupMeta? = meta

    override suspend fun uploadSnapshot(json: String): Result<CloudBackupMeta> {
        uploadCalls++
        uploadFailure?.let { return Result.failure(it) }
        return Result.success(
            CloudBackupMeta(
                fileId = "fake-file",
                modifiedTimeEpochMs = System.currentTimeMillis(),
                sizeBytes = json.length.toLong(),
            ),
        )
    }

    override suspend fun downloadSnapshot(): Result<String> {
        downloadFailure?.let { return Result.failure(it) }
        return Result.success("""{"version":1,"vehicles":[]}""")
    }
}
