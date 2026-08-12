package ru.autolog.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Product copy for cloud failures must stay Russian and specific.
 * FullFlow UI never hits real Drive — these cases are covered here + fake gateway UI tests.
 */
@RunWith(Parameterized::class)
class FormattersUserMessageTest(
    private val errorMessage: String?,
    private val causeMessage: String?,
    private val expected: String,
) {
    @Test
    fun mapsCloudFailuresToProductCopy() {
        val error = when {
            errorMessage == null -> null
            causeMessage != null -> Exception(errorMessage, Exception(causeMessage))
            else -> Exception(errorMessage)
        }
        assertEquals(
            expected,
            Formatters.userMessage(error, FALLBACK),
        )
    }

    companion object {
        private const val FALLBACK = "Не удалось сохранить в Google"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any?>> = listOf(
            arrayOf(
                "The user's Drive storage quota has been exceeded.",
                null,
                "На Google Диске закончилось место. Освободите место или войдите другим аккаунтом.",
            ),
            arrayOf(
                "403 Forbidden",
                "storageQuotaExceeded",
                "На Google Диске закончилось место. Освободите место или войдите другим аккаунтом.",
            ),
            arrayOf(
                "Access Not Configured. Drive API has not been used in project 123 before or it is disabled.",
                null,
                "Сохранение в Google пока недоступно. Попробуйте позже или напишите в поддержку.",
            ),
            arrayOf(
                "Unable to resolve host www.googleapis.com",
                null,
                "Нет связи с Google. Проверьте интернет и попробуйте снова.",
            ),
            arrayOf(
                "timeout",
                null,
                "Нет связи с Google. Проверьте интернет и попробуйте снова.",
            ),
            arrayOf(
                "NeedRemoteConsent",
                null,
                "Нужно снова разрешить доступ Google. Отвяжите аккаунт и привяжите заново.",
            ),
            arrayOf(
                "access_denied",
                null,
                "Google не дал доступ к сохранению. Отвяжите аккаунт и привяжите заново.",
            ),
            arrayOf(
                "GoogleJsonResponseException: 403 Forbidden",
                null,
                "Google отклонил сохранение. Проверьте место на Диске или попробуйте другой аккаунт.",
            ),
            arrayOf(
                "В Google пока нет резервной копии",
                null,
                "В Google пока нет резервной копии",
            ),
            arrayOf(
                "Some English stack noise from Play Services",
                null,
                FALLBACK,
            ),
            arrayOf(
                null,
                null,
                FALLBACK,
            ),
        )
    }
}
