package com.windrm.app.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What kind of value a saved setting holds. */
enum class BackupKind { STRING, DOUBLE, INT, BOOLEAN }

/** The file is not a settings backup this app can read. */
class BackupException(message: String) : Exception(message)

/**
 * The settings written to, and read back from, a small JSON file the user keeps: {"app":"WIND-RM","format":1,"settings":{...}}.
 * Only the values the caller lists as [BackupKind]s by name are read back, each only if it has the right type.
 */
object SettingsBackup {
    const val APP = "WIND-RM"
    const val FORMAT = 1
    private val json = Json { prettyPrint = true }

    fun encode(values: Map<String, Any>): String {
        val settings = buildJsonObject {
            values.toSortedMap().forEach { (name, value) ->
                when (value) {
                    is String -> put(name, value)
                    is Double -> put(name, value)
                    is Int -> put(name, value)
                    is Boolean -> put(name, value)
                }
            }
        }
        val root = buildJsonObject {
            put("app", APP)
            put("format", FORMAT)
            put("settings", settings)
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    /** The values of [text] whose names are in [kinds] and whose type matches; anything else in the file is ignored. */
    fun decode(text: String, kinds: Map<String, BackupKind>): Map<String, Any> {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupException("not a settings file")
        }
        if (root["app"]?.jsonPrimitive?.content != APP) throw BackupException("not a WIND-RM settings file")
        val format = root["format"]?.jsonPrimitive?.intOrNull ?: throw BackupException("no format number")
        if (format > FORMAT) throw BackupException("made by a newer version of the app")
        val settings = root["settings"] as? JsonObject ?: throw BackupException("no settings in the file")
        val out = LinkedHashMap<String, Any>()
        for ((name, kind) in kinds) {
            val primitive = settings[name] as? JsonPrimitive ?: continue
            val value: Any? = when (kind) {
                BackupKind.STRING -> if (primitive.isString) primitive.content else null
                BackupKind.DOUBLE -> if (!primitive.isString) primitive.doubleOrNull else null
                BackupKind.INT -> if (!primitive.isString) primitive.intOrNull else null
                BackupKind.BOOLEAN -> if (!primitive.isString) primitive.booleanOrNull else null
            }
            if (value != null) out[name] = value
        }
        return out
    }
}
