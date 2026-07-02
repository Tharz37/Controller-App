package com.example.controller.storage

import android.content.Context
import com.example.controller.model.Preset
import org.json.JSONObject
import java.io.File

/**
 * Presets live as individual JSON files under filesDir/presets/<id>.json.
 * That way "save" is a single small write and a corrupt/half-written file
 * can never take out every other preset.
 */
class PresetRepository(context: Context) {

    private val dir: File = File(context.filesDir, "presets").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("controller_prefs", Context.MODE_PRIVATE)

    fun listPresets(): List<Preset> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { Preset.fromJson(JSONObject(f.readText())) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun save(preset: Preset) {
        File(dir, "${preset.id}.json").writeText(preset.toJson().toString())
    }

    fun delete(presetId: String) {
        File(dir, "$presetId.json").delete()
    }

    fun load(presetId: String): Preset? =
        File(dir, "$presetId.json").takeIf { it.exists() }
            ?.let { runCatching { Preset.fromJson(JSONObject(it.readText())) }.getOrNull() }

    // --- Remembered Bluetooth device -------------------------------------

    var lastDeviceAddress: String?
        get() = prefs.getString("last_device_address", null)
        set(value) = prefs.edit().putString("last_device_address", value).apply()

    var lastUsedPresetId: String?
        get() = prefs.getString("last_preset_id", null)
        set(value) = prefs.edit().putString("last_preset_id", value).apply()
}
