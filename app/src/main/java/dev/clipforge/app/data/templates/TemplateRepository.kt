package dev.clipforge.app.data.templates

import android.content.Context
import dev.clipforge.app.model.ClipTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Built-in templates ship as a JSON asset (editable without touching Kotlin —
 * see assets/templates/default_templates.json). User-created templates are
 * appended and persisted to a small JSON file in app storage.
 */
class TemplateRepository private constructor(private val context: Context) {

    private val customFile = File(context.filesDir, "custom_templates.json")

    private val _templates = MutableStateFlow<List<ClipTemplate>>(emptyList())
    val templates: StateFlow<List<ClipTemplate>> = _templates.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val builtIns = parseTemplates(
            context.assets.open("templates/default_templates.json").bufferedReader().use { it.readText() },
            isBuiltIn = true,
        )
        val customs = if (customFile.exists()) {
            parseTemplates(customFile.readText(), isBuiltIn = false)
        } else emptyList()
        _templates.value = builtIns + customs
    }

    suspend fun addCustom(name: String, description: String, systemPrompt: String) = withContext(Dispatchers.IO) {
        val newTemplate = ClipTemplate(
            id = "custom_${UUID.randomUUID()}",
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            isBuiltIn = false,
        )
        val updatedCustoms = _templates.value.filter { !it.isBuiltIn } + newTemplate
        saveCustoms(updatedCustoms)
        _templates.value = _templates.value.filter { it.isBuiltIn } + updatedCustoms
    }

    suspend fun deleteCustom(id: String) = withContext(Dispatchers.IO) {
        val updatedCustoms = _templates.value.filter { !it.isBuiltIn && it.id != id }
        saveCustoms(updatedCustoms)
        _templates.value = _templates.value.filter { it.isBuiltIn } + updatedCustoms
    }

    private fun saveCustoms(customs: List<ClipTemplate>) {
        val arr = JSONArray()
        customs.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("description", it.description)
                put("system_prompt", it.systemPrompt)
            })
        }
        customFile.writeText(arr.toString())
    }

    private fun parseTemplates(jsonText: String, isBuiltIn: Boolean): List<ClipTemplate> {
        val arr = JSONArray(jsonText)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ClipTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                description = o.getString("description"),
                systemPrompt = o.getString("system_prompt"),
                isBuiltIn = isBuiltIn,
            )
        }
    }

    companion object {
        @Volatile private var instance: TemplateRepository? = null
        fun get(context: Context): TemplateRepository =
            instance ?: synchronized(this) {
                instance ?: TemplateRepository(context.applicationContext).also { instance = it }
            }
    }
}
