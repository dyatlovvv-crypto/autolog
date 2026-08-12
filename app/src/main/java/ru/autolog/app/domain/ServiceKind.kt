package ru.autolog.app.domain

enum class ServiceKind(val label: String) {
    TO("ТО"),
    REPAIR("Ремонт"),
    PLANNED("Запланированно");

    companion object {
        fun fromStorage(raw: String?): ServiceKind =
            entries.find { it.name == raw || it.label.equals(raw, ignoreCase = true) } ?: TO
    }
}
