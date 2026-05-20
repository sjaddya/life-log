package com.example.lifelog.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.domain.DaySettings
import com.example.lifelog.domain.EntrySource
import com.example.lifelog.domain.EntryStatus
import com.example.lifelog.domain.IntervalGenerator
import com.example.lifelog.domain.SupportedIntervals
import com.example.lifelog.recording.AudioRecorder
import com.example.lifelog.recording.AudioStart
import com.example.lifelog.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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
    val Green = Color(0xFF1F8A52)      // filled on time
    val GreenSoft = Color(0xFF7CC59A)  // backfilled — related but distinct
    val Blue = Color(0xFF3A7BD5)       // skipped — clearly not a green
}

@Composable
fun TimeAuditApp(
    viewModel: MainViewModel,
    initialEntryId: String?,
    launchToken: Int = 0
) {
    val state by viewModel.state.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.Setup) }
    var consumedDeepLinkId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.settings.setupComplete) {
        if (!state.settings.setupComplete) {
            destination = Destination.Setup
        } else if (destination == Destination.Setup) {
            destination = Destination.Timeline
        }
    }

    // A plain launcher re-open (launchToken bumped by MainActivity) should land
    // on the Timeline, not a stale Prompt / entry screen left over from before.
    LaunchedEffect(launchToken) {
        if (launchToken > 0 && state.settings.setupComplete) {
            destination = Destination.Timeline
        }
    }

    // One-shot deep-link consumption: route to Prompt for the notification's
    // entry once, then mark consumed so saves / config changes / re-emissions
    // of state.entries do not re-route the user.
    LaunchedEffect(initialEntryId) {
        val id = initialEntryId ?: return@LaunchedEffect
        if (id == consumedDeepLinkId) return@LaunchedEffect
        // Wait for today's entries to load (cold-start from notification can
        // race ensureTodayExists()).
        snapshotFlow { state.entries }.first { it.isNotEmpty() }
        viewModel.selectEntry(id)
        destination = Destination.Prompt
        consumedDeepLinkId = id
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
                fillOldestFirst = state.settings.fillOldestFirst,
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
            StatsScreen(entries = state.entries, settings = state.settings)
        }

        Destination.Settings -> Shell(
            selected = Destination.Settings,
            onNavigate = { destination = it }
        ) {
            SettingsScreen(
                settings = state.settings,
                hasTodayEntries = state.entries.isNotEmpty(),
                onSave = { wake, end, interval ->
                    viewModel.saveSetup(wake, end, interval)
                },
                onFillOrderChange = { viewModel.setFillOrder(it) }
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
    fillOldestFirst: Boolean,
    onEntryClick: (Entry) -> Unit,
    onQuickCheckIn: (Entry?) -> Unit
) {
    val completion = completionRatio(entries)
    val now = System.currentTimeMillis()
    val unfilledPast = entries
        .filter { it.endTime <= now && (it.status == EntryStatus.Pending || it.status == EntryStatus.Missed) }
    val candidate = if (fillOldestFirst) {
        unfilledPast.minByOrNull { it.endTime }
    } else {
        unfilledPast.maxByOrNull { it.endTime }
    }

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
                    Text("Just finished", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = entry?.let {
                            "${IntervalGenerator.formatClock(it.startTime)} – ${IntervalGenerator.formatClock(it.endTime)}"
                        } ?: "--:--",
                        color = AuditColors.Paper,
                        fontFamily = FontFamily.Serif,
                        fontSize = 36.sp,
                        lineHeight = 40.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("What did you do in this block?", color = AuditColors.Paper, fontSize = 18.sp)
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
    var text by rememberSaveable(entry?.id) { mutableStateOf(entry?.text.orEmpty()) }

    Surface(color = AuditColors.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(18.dp)
        ) {
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
                    focusedTextColor = AuditColors.Ink,
                    unfocusedTextColor = AuditColors.Ink,
                    disabledTextColor = AuditColors.Muted,
                    cursorColor = AuditColors.Ink,
                    focusedPlaceholderColor = AuditColors.Muted,
                    unfocusedPlaceholderColor = AuditColors.Muted,
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
    var errorText by remember { mutableStateOf<String?>(null) }
    var micBlocked by remember { mutableStateOf(false) }

    fun startRecording() {
        val selected = entry ?: return
        when (val result = recorder.start(selected.id)) {
            is AudioStart.Success -> {
                outputPath = result.path
                startedAt = System.currentTimeMillis()
                elapsedSeconds = 0L
                isRecording = true
                errorText = null
            }
            is AudioStart.Failure -> {
                outputPath = null
                isRecording = false
                errorText = "Recording failed: ${result.reason}"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micBlocked = false
            errorText = null
            startRecording()
        } else {
            // If the system will no longer show the rationale dialog, the
            // permission is effectively blocked — point the user at settings.
            val activity = context as? Activity
            val canAskAgain = activity != null && ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            micBlocked = !canAskAgain
            errorText = if (canAskAgain) {
                "Microphone access is needed to record. Tap Mic to allow it."
            } else {
                "Microphone access is blocked for LifeLog. Enable it in Android settings."
            }
        }
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
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp),
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
            errorText?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = AuditColors.Red, fontSize = 13.sp)
            }
            if (micBlocked) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }) { Text("Open settings", color = AuditColors.Amber) }
            }
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
private fun StatsScreen(entries: List<Entry>, settings: DaySettings) {
    val completed = entries.count { it.status == EntryStatus.Completed || it.status == EntryStatus.Backfilled }
    val missed = entries.count { it.status == EntryStatus.Missed }
    val skipped = entries.count { it.status == EntryStatus.Skipped }
    val backfilled = entries.count { it.status == EntryStatus.Backfilled }
    val voice = entries.count { it.source == EntrySource.VoiceRecording }
    val ratio = completionRatio(entries)
    val now = System.currentTimeMillis()
    val due = entries.count { it.endTime <= now }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Text("Today", fontFamily = FontFamily.Serif, fontSize = 38.sp, color = AuditColors.Ink)
            Spacer(modifier = Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CompletionRing(ratio = ratio, centerText = "$completed/$due")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Missed", "$missed", AuditColors.Red, Modifier.weight(1f))
                MetricCard("Backfilled", "$backfilled", AuditColors.GreenSoft, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Voice entries", "$voice", AuditColors.Purple, Modifier.weight(1f))
                MetricCard("Skipped", "$skipped", AuditColors.Blue, Modifier.weight(1f))
            }
        }
        item {
            Text("Entries filled by hour", color = AuditColors.Muted, fontSize = 13.sp)
            HourBars(
                entries = entries,
                startHour = settings.wakeMinutes / 60,
                endHour = ((settings.endMinutes - 1) / 60).coerceIn(0, 23)
            )
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
private fun HourBars(entries: List<Entry>, startHour: Int, endHour: Int) {
    val buckets = entries
        .filter { it.status == EntryStatus.Completed || it.status == EntryStatus.Backfilled }
        .groupBy { java.time.Instant.ofEpochMilli(it.startTime).atZone(java.time.ZoneId.systemDefault()).hour }

    if (buckets.isEmpty()) {
        Text(
            "No entries logged yet today.",
            color = AuditColors.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        return
    }

    val max = buckets.values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp).padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (startHour..endHour.coerceAtLeast(startHour)).forEach { hour ->
            val height = 18 + ((buckets[hour]?.size ?: 0).toFloat() / max * 96)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(AuditColors.Green.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: DaySettings,
    hasTodayEntries: Boolean,
    onSave: (Int, Int, Int) -> Unit,
    onFillOrderChange: (Boolean) -> Unit
) {
    var wake by remember(settings) { mutableStateOf(settings.wakeMinutes) }
    var end by remember(settings) { mutableStateOf(settings.endMinutes) }
    var interval by remember(settings) { mutableStateOf(settings.intervalMinutes) }
    var showTomorrowDialog by remember { mutableStateOf(false) }

    val scheduleChanged = wake != settings.wakeMinutes ||
        end != settings.endMinutes ||
        interval != settings.intervalMinutes

    if (showTomorrowDialog) {
        AlertDialog(
            onDismissRequest = { showTomorrowDialog = false },
            title = { Text("Takes effect tomorrow") },
            text = {
                Text(
                    "Today's check-ins keep their current schedule. " +
                        "Your new wake / end / interval applies from tomorrow."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTomorrowDialog = false
                    onSave(wake, end, interval)
                }) { Text("Save", color = AuditColors.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showTomorrowDialog = false }) {
                    Text("Cancel", color = AuditColors.Muted)
                }
            }
        )
    }

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
                onClick = {
                    if (hasTodayEntries && scheduleChanged) {
                        showTomorrowDialog = true
                    } else {
                        onSave(wake, end, interval)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AuditColors.Amber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save schedule") }
        }
        item {
            Text(
                "When several past slots are unfilled, \"Open current check-in\" goes to",
                color = AuditColors.Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to "Oldest first", false to "Newest first").forEach { (oldestFirst, label) ->
                    val selected = settings.fillOldestFirst == oldestFirst
                    Button(
                        onClick = { onFillOrderChange(oldestFirst) },
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) AuditColors.Amber else AuditColors.PaperAlt,
                            contentColor = if (selected) AuditColors.Paper else AuditColors.Ink
                        )
                    ) { Text(label) }
                }
            }
        }
        item {
            val context = LocalContext.current
            CapabilityRow("Notifications", settings.notificationPermissionGranted) {
                // Route to the system app-notification screen — always works,
                // including when the runtime permission is permanently denied.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
            CapabilityRow("Exact alarms", settings.exactAlarmAvailable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            }
            Text(
                text = "If exact alarms are unavailable, timeline logging still works. Tap a row above to fix it in Android settings — the status updates when you return.",
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
private fun CapabilityRow(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AuditColors.Ink)
            if (!active) {
                Text("Tap to fix in Android settings", color = AuditColors.Muted, fontSize = 11.sp)
            }
        }
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
    EntryStatus.Completed -> AuditColors.Green
    EntryStatus.Backfilled -> AuditColors.GreenSoft
    EntryStatus.Skipped -> AuditColors.Blue
    EntryStatus.Missed -> AuditColors.Red
    else -> AuditColors.Border
}

private fun completionRatio(entries: List<Entry>): Float {
    // Only slots whose time has actually passed count as "due" — future slots
    // haven't been asked yet, so they shouldn't drag the ratio down.
    val now = System.currentTimeMillis()
    val due = entries.count { it.endTime <= now }
    if (due == 0) return 0f
    val complete = entries.count {
        it.endTime <= now && (it.status == EntryStatus.Completed || it.status == EntryStatus.Backfilled)
    }
    return (complete.toFloat() / due).coerceIn(0f, 1f)
}
