package com.alaska.dailytracker

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/** A built-in (default) task definition — the "DAY seed". */
data class TaskDef(
    val id: String,
    val time: String,
    val icon: String,
    val title: String,
    val sub: String? = null,
    val exercises: List<Pair<String, String>>? = null,
    val custom: Boolean = false,
)

/** The default day. Order here is the original authoring order; display is sorted by time. */
val DAY: List<TaskDef> = listOf(
    TaskDef("wake", "11:30", "sunrise", "Пробуждение"),
    TaskDef(
        "warmup", "12:10", "dumbbell", "Утренняя разминка", sub = "10 минут",
        exercises = listOf(
            "Растяжка" to "пара минут буквально",
            "Упор рук" to "",
            "Разжим рук" to "",
            "Упор в голову" to "",
        )
    ),
    TaskDef("shower", "12:20", "snowflake", "Душ/Ванная", sub = "Максимум 30 минут"),
    TaskDef("work", "15:00", "check", "Работа", sub = "5-6 часов"),
    TaskDef("space", "16:00", "sun", "Космос", sub = "от 10 минут"),
    TaskDef("sleep", "23:59", "moon", "Сон", sub = "10 часов"),
)

/** Per-task overrides stored on top of the default. */
data class Ov(
    val time: String? = null,
    val title: String? = null,
    val sub: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
)

private const val PREFS = "dailyTracker"
private const val KEY = "state_v1"

/**
 * The "default + overrides" model, mirroring the HTML prototype's `store`.
 * Backed by SharedPreferences (JSON). All mutators persist immediately and
 * use observable state holders so Compose recomposes.
 */
class TrackerStore(private val ctx: Context) {

    val overrides = mutableStateMapOf<String, Ov>()
    val custom = mutableStateListOf<String>()

    init { load() }

    // ---- persistence ----
    private fun load() {
        overrides.clear(); custom.clear()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            val ovs = root.optJSONObject("overrides")
            ovs?.keys()?.forEach { id ->
                val o = ovs.getJSONObject(id)
                overrides[id] = Ov(
                    time = o.optStringOrNull("time"),
                    title = o.optStringOrNull("title"),
                    sub = o.optStringOrNull("sub"),
                    desc = if (o.has("desc")) o.getString("desc") else null,
                    icon = o.optStringOrNull("icon"),
                    done = o.optBoolean("done", false),
                    deleted = o.optBoolean("deleted", false),
                )
            }
            val cust = root.optJSONArray("custom")
            if (cust != null) for (i in 0 until cust.length()) custom.add(cust.getString(i))
        } catch (_: Exception) { /* corrupt store -> start fresh */ }
    }

    private fun save() {
        val ovs = JSONObject()
        overrides.forEach { (id, o) ->
            val j = JSONObject()
            o.time?.let { j.put("time", it) }
            o.title?.let { j.put("title", it) }
            o.sub?.let { j.put("sub", it) }
            o.desc?.let { j.put("desc", it) }
            o.icon?.let { j.put("icon", it) }
            if (o.done) j.put("done", true)
            if (o.deleted) j.put("deleted", true)
            ovs.put(id, j)
        }
        val root = JSONObject()
            .put("overrides", ovs)
            .put("custom", JSONArray(custom))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, root.toString()).apply()
    }

    // ---- the rendered task list: DAY + custom, minus deleted, sorted by time ----
    fun allTasks(): List<TaskDef> {
        val customDefs = custom.map { TaskDef(it, time = "", icon = ICON_ORDER[0], title = "", custom = true) }
        return (DAY + customDefs).filter { overrides[it.id]?.deleted != true }
    }

    fun sortedDay(): List<TaskDef> =
        allTasks().sortedBy { timeToMinutes(getTime(it)) } // Kotlin sortedBy is stable

    // ---- merged accessors (override ?? default) ----
    fun getTime(t: TaskDef): String = overrides[t.id]?.time ?: t.time
    fun getTitle(t: TaskDef): String = overrides[t.id]?.title ?: t.title
    fun getSub(t: TaskDef): String = overrides[t.id]?.sub ?: (t.sub ?: "")
    fun getIcon(t: TaskDef): String = overrides[t.id]?.icon ?: t.icon.ifEmpty { ICON_ORDER[0] }
    fun getDesc(t: TaskDef): String = overrides[t.id]?.desc ?: defaultDesc(t)
    fun isDone(t: TaskDef): Boolean = overrides[t.id]?.done == true

    private fun defaultDesc(t: TaskDef): String {
        val ex = t.exercises ?: return ""
        return ex.joinToString("\n") { (name, reps) -> if (reps.isNotEmpty()) "$name | $reps" else name }
    }

    // ---- mutations ----
    fun toggleDone(t: TaskDef): Boolean {
        val cur = overrides[t.id] ?: Ov()
        val becomingDone = !cur.done
        overrides[t.id] = cur.copy(done = becomingDone)
        save()
        return becomingDone
    }

    /** Save an edit/create. Returns the id that was written. */
    fun saveTask(id: String, creatingNew: Boolean, title: String, sub: String, desc: String, icon: String, time: String): String {
        if (creatingNew && !custom.contains(id)) custom.add(id)
        val cur = overrides[id] ?: Ov()
        val newTitle = title.trim().ifEmpty { if (creatingNew) "Новая задача" else (cur.title ?: "") }
        overrides[id] = cur.copy(
            title = newTitle,
            sub = sub.trim(),
            desc = desc,
            icon = icon,
            time = if (time.isNotEmpty()) time else cur.time,
        )
        save()
        return id
    }

    fun deleteTask(id: String) {
        if (id.startsWith("custom_")) {
            custom.remove(id)
            overrides.remove(id)
        } else {
            val cur = overrides[id] ?: Ov()
            overrides[id] = cur.copy(deleted = true)
        }
        save()
    }

    fun resetToDefault() {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        overrides.clear()
        custom.clear()
    }
}

/** "HH:MM" -> minutes; invalid/empty times sink to the bottom. */
fun timeToMinutes(str: String?): Int {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find((str ?: "").trim()) ?: return Int.MAX_VALUE
    return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
