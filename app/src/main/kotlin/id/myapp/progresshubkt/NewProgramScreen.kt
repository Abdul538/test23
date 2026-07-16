@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package id.myapp.progresshubkt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val dayLabelsFullId = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")

/**
 * "Program Baru" / first-run setup panel — the New Program panel, styled
 * after test21's onboarding ProgramSettingsScreen: a hero card with a
 * live start→goal weight preview, sectioned glass cards with gradient
 * icon badges, a rest-day chip picker, and a schedule preview, all
 * floating over the app's frosted GlassCard panels and drifting
 * ParticleBackground (already painted behind this screen by AppRoot).
 */
@Composable
fun NewProgramScreen(state: AppState) {
    val accent = AccentTeal
    var startWeightText by remember { mutableStateOf("0") }
    var goalWeightText by remember { mutableStateOf("0") }
    var weeksText by remember { mutableStateOf("0") }
    var restDays by remember { mutableStateOf(setOf("sat")) }
    var startDate by remember { mutableStateOf(todayEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val start = startWeightText.replace(',', '.').toDoubleOrNull()
    val goal = goalWeightText.replace(',', '.').toDoubleOrNull()
    val weeks = weeksText.toIntOrNull()

    fun save() {
        val sw = start ?: 0.0
        val gw = goal ?: 0.0
        val wk = weeks ?: 0
        val error = when {
            sw <= 0 || gw <= 0 -> "Isi dulu berat awal & berat target (tidak boleh 0)."
            wk < 1 -> "Jumlah minggu minimal 1."
            gw >= sw -> "Berat target harus lebih kecil dari berat awal."
            else -> null
        }
        if (error != null) {
            scope.launch { snackbarHostState.showSnackbar(error) }
            return
        }
        state.completeInitialSetup(
            ProgramSettings(
                startWeight = sw,
                goalWeight = gw,
                totalWeeks = wk,
                startDate = startDate,
                restDayKeys = restDays
            )
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NewProgramHeader(accent = accent)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OnboardingHero(
                    accent = accent,
                    start = start,
                    goal = goal,
                    weeks = weeks
                )

                SectionBlock(title = "TARGET BERAT BADAN", icon = Icons.Filled.MonitorWeight, accent = accent) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        NumberField(
                            label = "Berat awal (kg)",
                            value = startWeightText,
                            onChange = { startWeightText = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("→", color = Color(0xFF6B7688), fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Spacer(Modifier.width(10.dp))
                        NumberField(
                            label = "Berat target (kg)",
                            value = goalWeightText,
                            onChange = { goalWeightText = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SectionBlock(title = "DURASI PROGRAM", icon = Icons.Filled.CalendarMonth, accent = accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            label = "Jumlah minggu",
                            value = weeksText,
                            onChange = { weeksText = it },
                            decimal = false,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            StepperButton(symbol = "+") {
                                val v = (weeksText.toIntOrNull() ?: 0) + 1
                                weeksText = "$v"
                            }
                            Spacer(Modifier.height(6.dp))
                            StepperButton(symbol = "–") {
                                val v = ((weeksText.toIntOrNull() ?: 0) - 1).coerceAtLeast(1)
                                weeksText = "$v"
                            }
                        }
                    }
                }

                SectionBlock(title = "HARI MULAI PROGRAM", icon = Icons.Filled.Event, accent = accent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "TANGGAL MULAI",
                                color = TextDim,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${dayLabelsFullId[dowOf(startDate)]}, ${formatDateId(startDate)}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Text("Pilih")
                        }
                    }
                }

                SectionBlock(
                    title = "HARI LIBUR / ISTIRAHAT",
                    icon = Icons.Filled.Bedtime,
                    accent = accent,
                    subtitle = "Ketuk untuk menandai hari sebagai libur."
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (key in dayKeysByDow) {
                            val idx = dayKeysByDow.indexOf(key)
                            DayChip(
                                label = dayLabelsFullId[idx],
                                active = restDays.contains(key),
                                onTap = {
                                    restDays = if (restDays.contains(key)) restDays - key else restDays + key
                                }
                            )
                        }
                    }
                }

                SchedulePreview(
                    accent = accent,
                    settings = ProgramSettings(
                        startWeight = start ?: 0.0,
                        goalWeight = goal ?: 0.0,
                        totalWeeks = (weeks ?: 0).coerceAtLeast(1),
                        startDate = startDate,
                        restDayKeys = restDays
                    )
                )

                Button(
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Mulai Program", fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("→", fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDate * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDate = it / 86400000L }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun NewProgramHeader(accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF12161F), Color(0xFF0A0D12)))
            )
            .padding(16.dp, 20.dp, 16.dp, 22.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.DirectionsBike, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "MULAI PERJALANANMU",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Atur Target Program",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Tetapkan target berat & durasi — sisanya biar kita yang susun jadwalnya.",
            color = Color(0xFF9AA6B8),
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun OnboardingHero(accent: Color, start: Double?, goal: Double?, weeks: Int?) {
    val hasStart = start != null && start > 0
    val hasGoal = goal != null && goal > 0
    val delta = if (hasStart && hasGoal) start!! - goal!! else null

    GlassCard(modifier = Modifier.fillMaxWidth(), hero = true, borderColor = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.08f))))
                    .border(1.2.dp, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DirectionsBike, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "PROGRAM PENURUNAN BERAT",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tetapkan target kamu, kita yang urus jadwalnya",
                    color = Color(0xFF9AA6B8),
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (hasStart) formatKgShort(start!!) else "--",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "  →  ",
                color = Color(0xFF5A6577),
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 7.dp)
            )
            Text(
                if (hasGoal) formatKgShort(goal!!) else "--",
                color = accent,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "KG",
                color = TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        if (delta != null && delta > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                val label = if (weeks != null && weeks > 0)
                    "Turun ${delta.toInt()} kg dalam $weeks minggu"
                else
                    "Turun ${delta.toInt()} kg"
                Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "Isi target berat di bawah buat lihat perkiraan progres kamu",
                color = Color(0xFF6B7688),
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    icon: ImageVector,
    accent: Color,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f))))
                    .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = TextDim,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color(0xFF6B7688), fontSize = 11.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true
) {
    Column(modifier = modifier) {
        Text(label, color = TextDim, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepperButton(symbol: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1C222C))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DayChip(label: String, active: Boolean, onTap: () -> Unit) {
    val activeColor = Color(0xFF6B7688)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) activeColor.copy(alpha = 0.18f) else Color(0xFF10141B))
            .border(
                width = if (active) 1.4.dp else 1.dp,
                color = if (active) activeColor else Color(0xFF232A35),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else Color(0xFFA3AEBD)
        )
    }
}

@Composable
private fun SchedulePreview(accent: Color, settings: ProgramSettings) {
    val roles = computeDayRoles(settings)
    val week1End = settings.startDate + settings.week1Length - 1
    val isFullWeek = settings.week1Length == 7

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Explore, contentDescription = null, tint = TextDim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "CARA JADWAL DISUSUN",
                color = TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                buildString {
                    append("Minggu 1 mulai ${dayLabelsFullId[dowOf(settings.startDate)]}, ${formatDateId(settings.startDate)}")
                    append(", latihan sampai Sabtu, ${formatDateId(week1End)}")
                    if (!isFullWeek) append(" (${settings.week1Length} hari, lebih pendek).")
                    else append(".")
                },
                color = Color(0xFFB7C0CF),
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Minggu 2 dan seterusnya selalu genap Minggu–Sabtu, berulang otomatis sampai program selesai.",
                color = Color(0xFFB7C0CF),
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            Text("POLA TIAP HARI", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in dayKeysByDow) {
                    val idx = dayKeysByDow.indexOf(key)
                    RoleBadge(day = dayLabelsFullId[idx], role = roles[key] ?: "rest", accent = accent)
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(day: String, role: String, accent: Color) {
    val (label, color) = when (role) {
        "rest" -> "Libur" to Color(0xFF5A6577)
        "longrun" -> "Jarak jauh" to accent
        "heavy" -> "Berat" to Color(0xFFD16B5C)
        else -> "Ringan" to Color(0xFFE0A94E)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Column {
            Text(day.take(3), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun formatKgShort(v: Double): String {
    val rounded = Math.round(v * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
