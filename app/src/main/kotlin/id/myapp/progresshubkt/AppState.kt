package id.myapp.progresshubkt

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class AppState(private val context: Context) {
    private val prefs = context.getSharedPreferences("progress_hub_v1", Context.MODE_PRIVATE)
    private val storageKey = "state_json"

    var settings by mutableStateOf(ProgramSettings())
    var hasCompletedSetup by mutableStateOf(false)
    var week by mutableStateOf(1)
    var completed by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var actualKm by mutableStateOf<Map<String, Double>>(emptyMap())
    var weights by mutableStateOf<Map<String, Double>>(mapOf("0" to settings.startWeight))

    fun load() {
        val raw = prefs.getString(storageKey, null)
        hasCompletedSetup = raw != null
        if (raw != null) {
            try {
                val j = JSONObject(raw)
                if (j.has("settings")) settings = settingsFromJson(j.getJSONObject("settings"))
                hasCompletedSetup = j.optBoolean("hasCompletedSetup", true)
                week = j.optInt("week", 1)
                completed = j.optJSONObject("completed")?.toBoolMap() ?: emptyMap()
                actualKm = j.optJSONObject("actualKm")?.toDoubleMap() ?: emptyMap()
                weights = j.optJSONObject("weights")?.toDoubleMap() ?: mapOf("0" to settings.startWeight)
            } catch (_: Exception) { }
        }
        week = week.coerceIn(1, settings.totalWeeks)
    }

    private fun save() {
        val j = JSONObject()
        j.put("settings", settingsToJson(settings))
        j.put("hasCompletedSetup", hasCompletedSetup)
        j.put("week", week)
        j.put("completed", JSONObject(completed as Map<*, *>))
        j.put("actualKm", JSONObject(actualKm as Map<*, *>))
        j.put("weights", JSONObject(weights as Map<*, *>))
        prefs.edit().putString(storageKey, j.toString()).apply()
    }

    fun updateSettings(newSettings: ProgramSettings) {
        settings = ProgramSettings.normalize(newSettings)
        week = week.coerceIn(1, settings.totalWeeks)
        save()
    }

    fun completeInitialSetup(newSettings: ProgramSettings) {
        settings = ProgramSettings.normalize(newSettings)
        weights = mapOf("0" to settings.startWeight)
        hasCompletedSetup = true
        week = 1
        save()
    }

    fun toggleDayDone(week: Int, dayKey: String, defaultKm: Double) {
        val k = "$week-$dayKey"
        val isDone = completed[k] == true
        val newCompleted = completed.toMutableMap()
        newCompleted[k] = !isDone
        completed = newCompleted
        if (!isDone && !actualKm.containsKey(k)) {
            val newKm = actualKm.toMutableMap()
            newKm[k] = defaultKm
            actualKm = newKm
        }
        save()
    }

    fun setActualKm(week: Int, dayKey: String, km: Double) {
        val k = "$week-$dayKey"
        val newKm = actualKm.toMutableMap()
        newKm[k] = km
        actualKm = newKm
        save()
    }

    fun logWeight(kg: Double) {
        val dayOffset = todayEpochDay() - settings.startDate
        val newWeights = weights.toMutableMap()
        newWeights[dayOffset.toString()] = kg
        weights = newWeights
        save()
    }

    val currentWeight: Double
        get() {
            if (weights.isEmpty()) return settings.startWeight
            val latest = weights.keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: return settings.startWeight
            return weights[latest.toString()] ?: settings.startWeight
        }

    val weightHistorySorted: List<Pair<Int, Double>>
        get() = weights.entries.mapNotNull { e -> e.key.toIntOrNull()?.let { it to e.value } }.sortedBy { it.first }

    private fun allDays(): List<Pair<Int, DayPlan>> {
        val list = mutableListOf<Pair<Int, DayPlan>>()
        for (w in 1..settings.totalWeeks) {
            for (d in weekPlan(w, settings)) list.add(w to d)
        }
        return list
    }

    fun computeStreak(): Pair<Int, Int> {
        val all = allDays().filter { !it.second.rest }.sortedBy { it.second.date }
        val today = todayEpochDay()
        val past = all.filter { it.second.date <= today }
        var streak = 0
        for (i in past.indices.reversed()) {
            val k = "${past[i].first}-${past[i].second.key}"
            if (completed[k] == true) streak++ else break
        }
        var best = 0; var cur = 0
        for (e in all) {
            val k = "${e.first}-${e.second.key}"
            if (completed[k] == true) { cur++; if (cur > best) best = cur } else cur = 0
        }
        return streak to best
    }

    val totalKmAll: Double
        get() {
            var sum = 0.0
            for (e in allDays()) {
                val k = "${e.first}-${e.second.key}"
                if (completed[k] != true) continue
                actualKm[k]?.let { sum += it }
            }
            return sum
        }

    val totalCaloriesAll: Int
        get() {
            var sum = 0
            val w = currentWeight
            for (e in allDays()) {
                val k = "${e.first}-${e.second.key}"
                if (completed[k] != true) continue
                actualKm[k]?.let { sum += estimateCalories(w, it) }
            }
            return sum
        }

    val totalSessionsAll: Pair<Int, Int>
        get() {
            var done = 0; var total = 0
            for (e in allDays()) {
                if (e.second.rest) continue
                total++
                if (completed["${e.first}-${e.second.key}"] == true) done++
            }
            return done to total
        }

    val progressFraction: Double
        get() {
            val lost = settings.startWeight - currentWeight
            val goalLoss = settings.startWeight - settings.goalWeight
            if (goalLoss <= 0) return 0.0
            return (lost / goalLoss).coerceIn(0.0, 1.0)
        }

    fun goToWeek(w: Int) {
        week = w.coerceIn(1, settings.totalWeeks)
        save()
    }
}

private fun JSONObject.toBoolMap(): Map<String, Boolean> {
    val m = mutableMapOf<String, Boolean>()
    keys().forEach { k -> m[k] = optBoolean(k) }
    return m
}

private fun JSONObject.toDoubleMap(): Map<String, Double> {
    val m = mutableMapOf<String, Double>()
    keys().forEach { k -> m[k] = optDouble(k) }
    return m
}

private fun settingsToJson(s: ProgramSettings): JSONObject {
    val j = JSONObject()
    j.put("startWeight", s.startWeight)
    j.put("goalWeight", s.goalWeight)
    j.put("totalWeeks", s.totalWeeks)
    j.put("startDate", s.startDate)
    j.put("restDayKeys", JSONArray(s.restDayKeys.toList()))
    return j
}

private fun settingsFromJson(j: JSONObject): ProgramSettings {
    val d = ProgramSettings()
    val restArr = j.optJSONArray("restDayKeys")
    val rest = if (restArr != null) (0 until restArr.length()).map { restArr.getString(it) }.toSet() else d.restDayKeys
    return ProgramSettings.normalize(
        ProgramSettings(
            startWeight = j.optDouble("startWeight", d.startWeight),
            goalWeight = j.optDouble("goalWeight", d.goalWeight),
            totalWeeks = j.optInt("totalWeeks", d.totalWeeks),
            startDate = j.optLong("startDate", d.startDate),
            restDayKeys = rest
        )
    )
}
