package id.myapp.progresshubkt

import java.util.Calendar
import kotlin.math.roundToInt

val dayKeysByDow = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
val dayLabelsId = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")
val monthLabelsId = listOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
)
const val kCalorieFactorFlat = 0.32

// Days since epoch, used as a compact "date" representation (no time zone headaches).
typealias EpochDay = Long

fun todayEpochDay(): EpochDay {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis / 86400000L
}

fun epochDayToCalendar(day: EpochDay): Calendar {
    val c = Calendar.getInstance()
    c.timeInMillis = day * 86400000L
    return c
}

// Calendar.DAY_OF_WEEK: Sunday=1..Saturday=7 -> convert to 0=Sun..6=Sat
fun dowOf(day: EpochDay): Int = epochDayToCalendar(day).get(Calendar.DAY_OF_WEEK) - 1

fun formatDateId(day: EpochDay): String {
    val c = epochDayToCalendar(day)
    return "${c.get(Calendar.DAY_OF_MONTH)} ${monthLabelsId[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
}

data class ProgramSettings(
    val startWeight: Double = 110.0,
    val goalWeight: Double = 80.0,
    val totalWeeks: Int = 26,
    val startDate: EpochDay = todayEpochDay(),
    val restDayKeys: Set<String> = setOf("sat")
) {
    val startDow: Int get() = dowOf(startDate)
    val week1Length: Int get() = (6 - startDow) + 1
    val week2Start: EpochDay get() = startDate + week1Length

    val longRunDayKey: String
        get() = dayKeysByDow.firstOrNull { !restDayKeys.contains(it) } ?: dayKeysByDow.first()

    companion object {
        fun normalize(s: ProgramSettings): ProgramSettings {
            var rest = s.restDayKeys.filter { dayKeysByDow.contains(it) }.toSet()
            if (rest.size > 5) rest = rest.take(5).toSet()
            val weeks = if (s.totalWeeks < 1) 1 else s.totalWeeks
            return s.copy(totalWeeks = weeks, restDayKeys = rest)
        }
    }
}

class Phase(val name: String, val colorHex: Long)

class PhaseBounds(val b1: Int, val b2: Int, val b3: Int)

fun phaseBoundsFor(totalWeeks: Int): PhaseBounds {
    var b1 = (totalWeeks * 4.0 / 26).roundToInt()
    if (b1 < 1) b1 = 1
    if (b1 > totalWeeks) b1 = totalWeeks
    var b2 = (totalWeeks * 10.0 / 26).roundToInt()
    if (b2 <= b1) b2 = b1 + 1
    if (b2 > totalWeeks) b2 = totalWeeks
    var b3 = (totalWeeks * 20.0 / 26).roundToInt()
    if (b3 <= b2) b3 = b2 + 1
    if (b3 > totalWeeks) b3 = totalWeeks
    return PhaseBounds(b1, b2, b3)
}

fun phaseFor(week: Int, totalWeeks: Int): Phase {
    val b = phaseBoundsFor(totalWeeks)
    return when {
        week <= b.b1 -> Phase("Rebuild Base", 0xFF5FB3A3)
        week <= b.b2 -> Phase("Ramp Up", 0xFFE0A94E)
        week <= b.b3 -> Phase("Target Volume", 0xFFD16B5C)
        else -> Phase("Maintain & Deload", 0xFF7A8BA6)
    }
}

class TrainingLoad(val heavy: Double, val light: Double, val longRun: Double, val deload: Boolean)

fun targetsForWeek(week: Int, totalWeeks: Int): TrainingLoad {
    val b = phaseBoundsFor(totalWeeks)
    if (week <= b.b1) return TrainingLoad(30.0, 25.0, 40.0, false)
    if (week <= b.b2) {
        val v = (30 + (week - b.b1) * 2).coerceIn(0, 40).toDouble()
        val sun = (45 + (week - b.b1 - 1)).coerceIn(0, 50).toDouble()
        return TrainingLoad(v, v, sun, false)
    }
    if (week <= b.b3) {
        val isDeload = week % 4 == 0
        val base = 48.0
        val d = if (isDeload) Math.round(base * 0.65).toDouble() else base
        return TrainingLoad(d, d, if (isDeload) 35.0 else 52.0, isDeload)
    }
    val isDeload = (week - b.b3) % 4 == 0
    val base = 50.0
    val d = if (isDeload) Math.round(base * 0.6).toDouble() else base
    return TrainingLoad(d, d, if (isDeload) 35.0 else 54.0, isDeload)
}

fun computeDayRoles(s: ProgramSettings): Map<String, String> {
    val roles = mutableMapOf<String, String>()
    var nextHeavy = true
    for (key in dayKeysByDow) {
        if (s.restDayKeys.contains(key)) { roles[key] = "rest"; continue }
        if (key == s.longRunDayKey) { roles[key] = "longrun"; continue }
        roles[key] = if (nextHeavy) "heavy" else "light"
        nextHeavy = !nextHeavy
    }
    return roles
}

fun dateForSlot(week: Int, offset: Int, s: ProgramSettings): EpochDay {
    return if (week == 1) s.startDate + offset else s.week2Start + (week - 2) * 7 + offset
}

class DayPlan(val key: String, val label: String, val date: EpochDay, val km: Double, val rest: Boolean)

fun weekPlan(week: Int, s: ProgramSettings): List<DayPlan> {
    val t = targetsForWeek(week, s.totalWeeks)
    val roles = computeDayRoles(s)
    val length = if (week == 1) s.week1Length else 7
    val days = mutableListOf<DayPlan>()
    for (offset in 0 until length) {
        val d = dateForSlot(week, offset, s)
        val dow = dowOf(d)
        val key = dayKeysByDow[dow]
        val role = roles[key] ?: "rest"
        val rest = role == "rest"
        val km = when (role) {
            "longrun" -> t.longRun
            "heavy" -> t.heavy
            "light" -> t.light
            else -> 0.0
        }
        days.add(DayPlan(key, dayLabelsId[dow], d, if (rest) 0.0 else km, rest))
    }
    return days
}

fun estimateCalories(weightKg: Double, km: Double): Int = (weightKg * km * kCalorieFactorFlat).roundToInt()
