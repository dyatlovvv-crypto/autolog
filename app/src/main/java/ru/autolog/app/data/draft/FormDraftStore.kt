package ru.autolog.app.data.draft

import android.content.Context
import org.json.JSONObject
import ru.autolog.app.domain.ServiceKind

/**
 * App-local drafts for unfinished forms.
 * Survives leaving the screen and process death beyond SavedStateHandle lifetime.
 * Cleared only on intentional save or delete.
 */
class FormDraftStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class ServiceDraft(
        val amount: String = "",
        val odometer: String = "",
        val dateEpochMs: Long = 0L,
        val description: String = "",
        val checklistDraft: String = "",
        val checklistJson: String = "[]",
        val photoPathsJson: String = "[]",
    ) {
        fun isEmpty(): Boolean =
            amount.isBlank() &&
                odometer.isBlank() &&
                description.isBlank() &&
                checklistDraft.isBlank() &&
                (checklistJson.isBlank() || checklistJson == "[]") &&
                (photoPathsJson.isBlank() || photoPathsJson == "[]")
    }

    data class RefuelDraft(
        val odometer: String = "",
        val liters: String = "",
        val price: String = "",
        val isTankFull: Boolean = true,
        val station: String = "",
        val fuelType: String = "",
    ) {
        fun isEmpty(): Boolean =
            odometer.isBlank() && liters.isBlank() && price.isBlank() && station.isBlank()
    }

    fun saveServiceDraft(vehicleId: Long, kind: ServiceKind, draft: ServiceDraft) {
        if (draft.isEmpty()) {
            clearServiceDraft(vehicleId, kind)
            return
        }
        val json = JSONObject()
            .put("amount", draft.amount)
            .put("odometer", draft.odometer)
            .put("dateEpochMs", draft.dateEpochMs)
            .put("description", draft.description)
            .put("checklistDraft", draft.checklistDraft)
            .put("checklistJson", draft.checklistJson)
            .put("photoPathsJson", draft.photoPathsJson)
            .toString()
        prefs.edit().putString(serviceKey(vehicleId, kind), json).apply()
    }

    fun loadServiceDraft(vehicleId: Long, kind: ServiceKind): ServiceDraft? {
        val raw = prefs.getString(serviceKey(vehicleId, kind), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            ServiceDraft(
                amount = o.optString("amount"),
                odometer = o.optString("odometer"),
                dateEpochMs = o.optLong("dateEpochMs"),
                description = o.optString("description"),
                checklistDraft = o.optString("checklistDraft"),
                checklistJson = o.optString("checklistJson", "[]"),
                photoPathsJson = o.optString("photoPathsJson", "[]"),
            ).takeUnless { it.isEmpty() }
        }.getOrNull()
    }

    fun clearServiceDraft(vehicleId: Long, kind: ServiceKind) {
        prefs.edit().remove(serviceKey(vehicleId, kind)).apply()
    }

    fun saveRefuelDraft(vehicleId: Long, draft: RefuelDraft) {
        if (draft.isEmpty()) {
            clearRefuelDraft(vehicleId)
            return
        }
        val json = JSONObject()
            .put("odometer", draft.odometer)
            .put("liters", draft.liters)
            .put("price", draft.price)
            .put("isTankFull", draft.isTankFull)
            .put("station", draft.station)
            .put("fuelType", draft.fuelType)
            .toString()
        prefs.edit().putString(refuelKey(vehicleId), json).apply()
    }

    fun loadRefuelDraft(vehicleId: Long): RefuelDraft? {
        val raw = prefs.getString(refuelKey(vehicleId), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            RefuelDraft(
                odometer = o.optString("odometer"),
                liters = o.optString("liters"),
                price = o.optString("price"),
                isTankFull = o.optBoolean("isTankFull", true),
                station = o.optString("station"),
                fuelType = o.optString("fuelType"),
            ).takeUnless { it.isEmpty() }
        }.getOrNull()
    }

    fun clearRefuelDraft(vehicleId: Long) {
        prefs.edit().remove(refuelKey(vehicleId)).apply()
    }

    private fun serviceKey(vehicleId: Long, kind: ServiceKind) =
        "service_draft_${vehicleId}_${kind.name}"

    private fun refuelKey(vehicleId: Long) = "refuel_draft_$vehicleId"

    companion object {
        private const val PREFS = "autolog_form_drafts"
    }
}
