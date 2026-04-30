package com.example.lifelog.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.domain.DaySettings
import com.example.lifelog.domain.EntrySource
import com.example.lifelog.domain.EntryStatus
import com.example.lifelog.domain.IntervalGenerator
import com.example.lifelog.domain.SupportedIntervals
import com.example.lifelog.recording.AudioRecorder
import com.example.lifelog.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private enum class Destination { Setup, Timeline, Stats, Settings, Prompt, TextEntry, VoiceEntry }

private object AuditColors {
    val Ink = Color(0xFF1A1917)
    val Paper = Color(0xFFFAF9F6)
    val PaperAlt = Color(0xFFF3F1EB)
    val Amber = Color(0xFFBA7517)
    val AmberSoft = Color(0xFFFAEEDA)
    val Muted = Color(0xFF5F5E5A)
    val Border = Color(0x22302F2D)
    val Red = Color(0xFFE24B4A)
    val Teal = Color(0xFF1D9E75)
    val Purple = Color(0xFF7F77DD)
    val Gray = Color(0xFF888780)
}

@Composable
fun TimeAuditApp(
    viewModel: MainViewModel,
    initialEntryId: String?
) {
    val state by viewModel.state.collectAsState()
    var destination by remember { mutableStateOf(Destination.Setup) }

    LaunchedEffect(state.settings.setupComplete) {
        if (!state.settings.setupComplete) {
            destination = Destination.Setup
        } else if (destination == Destination.Setup) {
            destination = Destination.Timeline
        }
    }

    LaunchedEffect(initialEntryId, state.entries) {
        if (initialEntryId != null && state.entries.isNotEmpty() && state.activeEntry?.id != initialEntryId) {
            viewModel.selectEntry(initialEntryId)
            destination = Destination.Prompt
        }
    }

    when (destination) {
        Destination.Setup -> SetupScreen(
            settings = state.settings,
            onSave = { wake, end, interval ->
                viewModel.saveSetup(wake, end, interval) {
                    destination = Destination.Timeline
                }
            }
        )

        Destination.Timeline -> Shell(
            selected = Destination.Timeline,
            onNavigate = { destination = it }
        ) {
            TimelineScreen(
                entries = state.entries,
                onEntryClick = {
                    viewModel.selectEntry(it.id)
                    destination = Destination.TextEntry
                },
                onQuickCheckIn = {
                    it?.let { entry ->
                        viewModel.selectEntry(entry.id)
                        destination = Destination.Prompt
                    }
                }
            )
        }

        Destination.Stats -> Shell(
            selected = Destination.Stats,
            onNavigate = { destination = it }
        ) {
            StatsScreen(entries = state.entries)
        }

        Destination.Settings -> Shell(
            selected = Destination.Settings,
            onNavigate = { destination = it }
        ) {
            SettingsScreen(
                settings = state.settings,
                onSave = { wake, end, interval ->
                    viewModel.saveSetup(wake, end, interval)
                }
            )
        }

        Destination.Prompt -> CheckInPrompt(
            entry = state.activeEntry,
            onLogNow = { destination = Destination.TextEntry },
            onRemindLater = {
                state.activeEntry?.let { entry ->
                    viewModel.remindLater(entry.id) { destination = Destination.Timeline }
                }
            },
            onSkip = {
                state.activeEntry?.let { entry ->
                    viewModel.skip(entry.id) { destination = Destination.Timeline }
                }
            }
        )

        Destination.TextEntry -> LogEntryScreen(
            entry = state.activeEntry,
            onSave = { entry, text ->
                viewModel.saveText(entry.id, text) { destination = Destination.Timeline }
            },
            onVoice = { destination = Destination.VoiceEntry },
            onCancel = { destination = Destination.Timeline }
        )

        Destination.VoiceEntry -> VoiceRecordingScreen(
            entry = state.activeEntry,
            onAttach = { entry, path ->
                viewModel.attachAudio(entry.id, path) { destination = Destination.Timeline }
            },
            onText = { destination = Destination.TextEntry },
            onCancel = { destination = Destination.Timeline }
        )
    }
}

@Composable
private fun Shell(
    selected: Destination,
    onNavigate: (Destination) -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = AuditColors.Paper,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuditColors.Paper)
                    .border(1.dp, AuditColors.Border)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavButton("Today", selected == Destination.Timeline) { onNavigate(Destination.Timeline) }
                NavButton("Stats", selected == Destination.Stats) { onNavigate(Destination.Stats) }
                NavButton("Settings", selected == Destination.Settings) { onNavigate(Destination.Settings) }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            content()
        }
    }
}

@Composable
private fun NavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (selected) AuditColors.Amber else AuditColors.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SetupScreen(
    settings: DaySettings,
    onSave: (Int, Int, Int) -> Unit
) {
    var wake by remember(settings) { mutableStateOf(settings.wakeMinutes) }
    var end by remember(settings) { mutableStateOf(settings.endMinutes) }
    var interval by remember(settings) { mutableStateOf(settings.intervalMinutes) }

    Surface(color = AuditColors.Ink, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "How does your day run?",
                    color = AuditColors.Paper,
                    fontFamily = FontFamily.Serif,
                    fontSize = 42.sp,
                    lineHeight = 46.sp
                )
                Spacer(modifier = Modifier.height(36.dp))
                TimeSettingCard("Wake time", wake) { wake = (wake + it).coerceIn(0, end - 15) }
                Divider(color = Color.White.copy(alpha = 0.10f))
                TimeSettingCard("End time", end) { end = (end + it).coerceIn(wake + 15, 24 * 60) }
                Divider(color = Color.White.copy(alpha = 0.10f))
                IntervalSelector(interval) { interval = it }
            }

            Button(
                onClick = { onSave(wake, end, interval) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuditColors.Amber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Start logging", color = AuditColors.Paper, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TimeSettingCard(label: String, value: Int, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
            Text(
                IntervalGenerator.formatMinutes(value),
                color = AuditColors.Paper,
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallDarkButton("-15") { onStep(-15) }
            SmallDarkButton("+15") { onStep(15) }
        }
    }
}

@Composable
private fun SmallDarkButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuditColors.Paper)
    ) {
        Text(label)
    }
}

@Composable
private fun IntervalSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 18.dp)) {
        Text("Log every", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportedIntervals.forEach { interval ->
                Button(
                    onClick = { onSelect(interval) },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == interval) AuditColors.Amber else Color.White.copy(alpha = 0.08f),
                        contentColor = AuditColors.Paper
                    )
                ) {
                    Text("$interval")
                }
            }
        }
    }
}

@Composable
private fun TimelineScreen(
    entries: List<Entry>,
    onEntryClick: (Entry) -> Unit,
    onQuickCheckIn: (Entry?) -> Unit
) {
    val completion = completionRatio(entries)
    val candidate = entries.firstOrNull { System.currentTimeMillis() in it.startTime until it.endTime }
        ?: entries.firstOrNull { it.status == EntryStatus.Pending || it.status == EntryStatus.Missed }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    modifier = Modifier.weight(1f),
                    color = AuditColors.Ink,
                    fontFamily = FontFamily.Serif,
                    fontSize = 34.sp,
                    lineHeight = 38.sp
                )
                Chip("${(completion * 100).toInt()}% complete", AuditColors.AmberSoft, AuditColors.Amber)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onQuickCheckIn(candidate) },
                enabled = candidate != null,
                colors = ButtonDefaults.buttonColors(containerColor = AuditColors.Amber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open current check-in")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(entries, key = { it.id }) { entry ->
            TimelineRow(entry = entry, onClick = { onEntryClick(entry) })
        }
    }
}

@Composable
private fun TimelineRow(entry: Entry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AuditColors.PaperAlt)
            .clickable(onClick = onClick)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(statusColor(entry.status))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "${IntervalGenerator.formatClock(entry.startTime)} - ${IntervalGenerator.formatClock(entry.endTime)}",
                color = AuditColors.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = entry.text?.takeIf { it.isNotBlank() }
                    ?: when (entry.status) {
                        EntryStatus.Missed -> "Missed"
                        EntryStatus.Skipped -> "Skipped"
                        else -> "What were you doing?"
                    },
                color = if (entry.text.isNullOrBlank()) AuditColors.Muted else AuditColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.audioPath != null) {
            Text("Audio", modifier = Modifier.padding(end = 12.dp), color = AuditColors.Purple, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CheckInPrompt(
    entry: Entry?,
    onLogNow: () -> Unit,
    onRemindLater: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(color = AuditColors.Ink, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF24221F)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Check-in", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = entry?.let { IntervalGenerator.formatClock(it.startTime) } ?: "--:--",
                        color = AuditColors.Paper,
                        fontFamily = FontFamily.Serif,
                        fontSize = 58.sp
                    )
                    Text("What have you been up to?", color = AuditColors.Paper, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = onLogNow,
                        enabled = entry != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuditColors.Amber),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Log now") }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRemindLater,
                        enabled = entry != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Remind me later", color = AuditColors.Paper) }
                    TextButton(onClick = onSkip, enabled = entry != null) {
                        Text("Skip this one", color = Color.White.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogEntryScreen(
    entry: Entry?,
    onSave: (Entry, String) -> Unit,
    onVoice: () -> Unit,
    onCancel: () -> Unit
) {
    var text by remember(entry?.id) { mutableStateOf(entry?.text.orEmpty()) }

    Surface(color = AuditColors.Paper, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(38.dp).background(AuditColors.Amber))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = entry?.let { "${IntervalGenerator.formatClock(it.startTime)} -> ${IntervalGenerator.formatClock(it.endTime)}" } ?: "Loading",
                    color = AuditColors.Ink,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What were you doing?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AuditColors.Paper,
                    unfocusedContainerColor = AuditColors.Paper,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onVoice, enabled = entry != null) { Text("Voice", color = AuditColors.Amber) }
                Text("${text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size} words", color = AuditColors.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel", color = AuditColors.Muted) }
                Button(
                    onClick = { entry?.let { onSave(it, text) } },
                    enabled = entry != null && text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuditColors.Amber),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Save entry") }
            }
        }
    }
}

@Composable
private fun VoiceRecordingScreen(
    entry: Entry?,
    onAttach: (Entry, String) -> Unit,
    onText: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var samples by remember { mutableStateOf(List(28) { 0.12f }) }

    fun startRecording() {
        val selected = entry ?: return
        outputPath = recorder.start(selected.id)
        startedAt = System.currentTimeMillis()
        elapsedSeconds = 0L
        isRecording = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(250)
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
            val normalized = (recorder.amplitude / 32767f).coerceIn(0.08f, 1f)
            samples = samples.drop(1) + normalized
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) recorder.cancel()
        }
    }

    Surface(color = AuditColors.Ink, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = entry?.let { "${IntervalGenerator.formatClock(it.startTime)} - ${IntervalGenerator.formatClock(it.endTime)}" } ?: "Loading",
                color = Color.White.copy(alpha = 0.62f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Waveform(samples = samples)
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "0:%02d".format(elapsedSeconds),
                color = AuditColors.Paper,
                fontFamily = FontFamily.Monospace,
                fontSize = 42.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(AuditColors.Amber)
                    .clickable {
                        if (isRecording) {
                            val path = recorder.stop() ?: outputPath
                            isRecording = false
                            if (entry != null && path != null) onAttach(entry, path)
                        } else {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isRecording) "Stop" else "Mic", color = AuditColors.Paper, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TextButton(onClick = {
                    if (isRecording) recorder.cancel()
                    isRecording = false
                    onText()
                }) { Text("Switch to text", color = AuditColors.Paper) }
                TextButton(onClick = {
                    if (isRecording) recorder.cancel()
                    isRecording = false
                    onCancel()
                }) { Text("Cancel", color = Color.White.copy(alpha = 0.64f)) }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Waveform(samples: List<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(84.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        samples.forEachIndexed { index, sample ->
            val color = if (index % 4 == 0) AuditColors.Paper else AuditColors.Amber
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14 + sample * 68).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.80f))
            )
        }
    }
}

@Composable
private fun StatsScreen(entries: List<Entry>) {
    val completed = entries.count { it.status == EntryStatus.Completed || it.status == EntryStatus.Backfilled }
    val missed = entries.count { it.status == EntryStatus.Missed }
    val skipped = entries.count { it.status == EntryStatus.Skipped }
    val backfilled = entries.count { it.status == EntryStatus.Backfilled }
    val voice = entries.count { it.source == EntrySource.VoiceRecording }
    val ratio = completionRatio(entries)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text("Today", fontFamily = FontFamily.Serif, fontSize = 38.sp, color = AuditColors.Ink)
            Spacer(modifier = Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CompletionRing(ratio = ratio, centerText = "$completed/${entries.size}")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Missed", "$missed", AuditColors.Red, Modifier.weight(1f))
                MetricCard("Backfilled", "$backfilled", AuditColors.Teal, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Voice entries", "$voice", AuditColors.Purple, Modifier.weight(1f))
                MetricCard("Skipped", "$skipped", AuditColors.Gray, Modifier.weight(1f))
            }
        }
        item {
            Text("Activity density", color = AuditColors.Muted, fontSize = 13.sp)
            HourBars(entries = entries)
        }
    }
}

@Composable
private fun CompletionRing(ratio: Float, centerText: String) {
    Box(modifier = Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            drawArc(AuditColors.Border, -90f, 360f, false, style = stroke)
            drawArc(AuditColors.Amber, -90f, 360f * ratio, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, fontFamily = FontFamily.Serif, fontSize = 38.sp, color = AuditColors.Ink)
            Text("entries logged", color = AuditColors.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(104.dp),
        colors = CardDefaults.cardColors(containerColor = AuditColors.PaperAlt),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = AuditColors.Muted, fontSize = 13.sp)
            Text(value, color = color, fontFamily = FontFamily.Serif, fontSize = 34.sp)
        }
    }
}

@Composable
private fun HourBars(entries: List<Entry>) {
    val buckets = entries
        .filter { it.status == EntryStatus.Completed || it.status == EntryStatus.Backfilled }
        .groupBy { java.time.Instant.ofEpochMilli(it.startTime).atZone(java.time.ZoneId.systemDefault()).hour }
    val max = buckets.values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp).padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (7..23).forEach { hour ->
            val height = 18 + ((buckets[hour]?.size ?: 0).toFloat() / max * 96)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(AuditColors.Amber.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: DaySettings,
    onSave: (Int, Int, Int) -> Unit
) {
    var wake by remember(settings) { mutableStateOf(settings.wakeMinutes) }
    var end by remember(settings) { mutableStateOf(settings.endMinutes) }
    var interval by remember(settings) { mutableStateOf(settings.intervalMinutes) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text("Settings", fontFamily = FontFamily.Serif, fontSize = 38.sp, color = AuditColors.Ink)
        }
        item {
            SettingsStepper("Wake time", IntervalGenerator.formatMinutes(wake)) {
                wake = (wake + it).coerceIn(0, end - 15)
            }
            SettingsStepper("End time", IntervalGenerator.formatMinutes(end)) {
                end = (end + it).coerceIn(wake + 15, 24 * 60)
            }
            Text("Interval", color = AuditColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportedIntervals.forEach {
                    Button(
                        onClick = { interval = it },
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (interval == it) AuditColors.Amber else AuditColors.PaperAlt,
                            contentColor = if (interval == it) AuditColors.Paper else AuditColors.Ink
                        )
                    ) { Text("$it") }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSave(wake, end, interval) },
                colors = ButtonDefaults.buttonColors(containerColor = AuditColors.Amber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save schedule") }
        }
        item {
            CapabilityRow("Notifications", settings.notificationPermissionGranted)
            CapabilityRow("Exact alarms", settings.exactAlarmAvailable)
            Text(
                text = "If exact alarms are unavailable, timeline logging still works. Enable alarms in Android settings for reliable check-ins.",
                color = AuditColors.Gray,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun SettingsStepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AuditColors.Muted, fontSize = 13.sp)
            Text(value, color = AuditColors.Ink, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
        }
        OutlinedButton(onClick = { onStep(-15) }) { Text("-15") }
        Spacer(modifier = Modifier.width(6.dp))
        OutlinedButton(onClick = { onStep(15) }) { Text("+15") }
    }
}

@Composable
private fun CapabilityRow(label: String, active: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = AuditColors.Ink)
        Chip(if (active) "Available" else "Needs attention", if (active) Color(0xFFE1F5EE) else Color(0xFFFCEBEB), if (active) AuditColors.Teal else AuditColors.Red)
    }
}

@Composable
private fun Chip(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = foreground, fontSize = 12.sp)
    }
}

private fun statusColor(status: String): Color = when (status) {
    EntryStatus.Completed -> AuditColors.Amber
    EntryStatus.Backfilled -> AuditColors.Teal
    EntryStatus.Missed -> AuditColors.Red
    EntryStatus.Skipped -> AuditColors.Gray
    else -> AuditColors.Border
}

private fun completionRatio(entries: List<Entry>): Float {
    if (entries.isEmpty()) return 0f
    val complete = entries.count { it.status == EntryStatus.Completed || it.status == EntryStatus.Backfilled }
    return complete.toFloat() / entries.size
}
