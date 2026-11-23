package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryNote(
    val id: String = UUID.randomUUID().toString().take(10),
    val title: String,
    val content: String,
    val pinned: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * User-controlled local Memory Vault for Julie AI.
 * Strictly on-device by default: users can view, add, pin, export JSON, or delete any note anytime.
 */
object MemoryVaultStore {
    private const val PREFS = "julie_memory_vault_v1"
    private const val KEY_NOTES = "notes_json"

    private val _notesFlow = MutableStateFlow<List<MemoryNote>>(emptyList())
    val notesFlow: StateFlow<List<MemoryNote>> = _notesFlow.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_NOTES, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<MemoryNote>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    MemoryNote(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        content = o.optString("content"),
                        pinned = o.optBoolean("pinned", false),
                        createdAtMs = o.optLong("createdAtMs", 0L)
                    )
                )
            }
            _notesFlow.value = list.sortedWith(compareByDescending<MemoryNote> { it.pinned }.thenByDescending { it.createdAtMs })
        }
    }

    fun addNote(context: Context, title: String, content: String, pinned: Boolean = false) {
        val cleanTitle = title.trim().take(100)
        val cleanContent = content.trim().take(1000)
        if (cleanTitle.isEmpty() && cleanContent.isEmpty()) return
        val note = MemoryNote(
            title = cleanTitle.ifBlank { cleanContent.take(36) },
            content = cleanContent.ifBlank { cleanTitle },
            pinned = pinned
        )
        val updated = (listOf(note) + _notesFlow.value)
            .sortedWith(compareByDescending<MemoryNote> { it.pinned }.thenByDescending { it.createdAtMs })
            .take(100)
        _notesFlow.value = updated
        persist(context, updated)
    }

    fun togglePin(context: Context, id: String) {
        val updated = _notesFlow.value.map {
            if (it.id == id) it.copy(pinned = !it.pinned) else it
        }.sortedWith(compareByDescending<MemoryNote> { it.pinned }.thenByDescending { it.createdAtMs })
        _notesFlow.value = updated
        persist(context, updated)
    }

    fun deleteNote(context: Context, id: String) {
        val updated = _notesFlow.value.filterNot { it.id == id }
        _notesFlow.value = updated
        persist(context, updated)
    }

    fun clearAll(context: Context) {
        _notesFlow.value = emptyList()
        persist(context, emptyList())
    }

    fun pinnedSummaryContext(): String {
        val pinned = _notesFlow.value.filter { it.pinned }.take(5)
        if (pinned.isEmpty()) return ""
        return pinned.joinToString("\n") { "- ${it.title}: ${it.content}" }
    }

    fun exportAsJson(): String {
        val arr = JSONArray()
        _notesFlow.value.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("pinned", n.pinned)
                    put("createdAtMs", n.createdAtMs)
                }
            )
        }
        return arr.toString(2)
    }

    private fun persist(context: Context, notes: List<MemoryNote>) {
        runCatching {
            val arr = JSONArray()
            notes.forEach { n ->
                arr.put(
                    JSONObject().apply {
                        put("id", n.id)
                        put("title", n.title)
                        put("content", n.content)
                        put("pinned", n.pinned)
                        put("createdAtMs", n.createdAtMs)
                    }
                )
            }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NOTES, arr.toString())
                .apply()
        }
    }
}
