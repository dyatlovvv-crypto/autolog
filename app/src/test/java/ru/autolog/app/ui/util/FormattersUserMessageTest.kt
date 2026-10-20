package ru.autolog.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FormattersUserMessageTest(
    private val errorMessage: String?,
    private val causeMessage: String?,
    private val expected: String,
) {
    @Test
    fun mapsFailuresToProductCopy() {
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
        private const val FALLBACK = "Не удалось сохранить копию"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any?>> = listOf(
            arrayOf(
                "Unable to resolve host example.com",
                null,
                "Нет связи. Проверьте интернет и попробуйте снова.",
            ),
            arrayOf(
                "timeout",
                null,
                "Нет связи. Проверьте интернет и попробуйте снова.",
            ),
            arrayOf(
                "ENOSPC: no space left on device",
                null,
                "Не хватает места на устройстве.",
            ),
            arrayOf(
                "Файл повреждён",
                null,
                "Файл повреждён",
            ),
            arrayOf(
                "Some English stack noise",
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
