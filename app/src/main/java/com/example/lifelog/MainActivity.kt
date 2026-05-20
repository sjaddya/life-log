package com.example.lifelog

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lifelog.data.SettingsRepository
import com.example.lifelog.data.TimeAuditRepository
import com.example.lifelog.data.local.AppDatabase
import com.example.lifelog.notifications.CheckInReceiver
import com.example.lifelog.notifications.CheckInScheduler
import com.example.lifelog.recording.AudioRetentionWorker
import com.example.lifelog.ui.TimeAuditApp
import com.example.lifelog.ui.theme.LifeLogTheme
import com.example.lifelog.ui.viewmodel.MainViewModel
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val activeEntryId = mutableStateOf<String?>(null)
    // Bumped on every non-deep-link (re)launch so the Compose layer can reset a
    // stale destination (e.g. a leftover Prompt screen) back to Timeline.
    private val launchToken = mutableIntStateOf(0)

    private val repository: TimeAuditRepository by lazy {
        TimeAuditRepository(
            AppDatabase.getInstance(applicationContext),
            SettingsRepository(applicationContext)
        )
    }
    private val scheduler: CheckInScheduler by lazy { CheckInScheduler(applicationContext) }

    // Held by the activity's ViewModelStore, so it survives configuration
    // changes — init (ensureTodayExists, scheduling, the missed-sweep ticker)
    // runs once per process, not once per rotation.
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(repository, scheduler) as T
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshSystemState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialId = intent.checkInEntryId()
        activeEntryId.value = initialId
        if (initialId != null) {
            // Neuter the held intent so future getIntent() reads (e.g. on
            // rotation) don't resurrect the deep link and re-route into Prompt.
            setIntent(Intent(this, MainActivity::class.java))
        }

        // Daily cleanup of voice recordings past the retention window.
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            AudioRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AudioRetentionWorker>(1, TimeUnit.DAYS).build()
        )

        enableEdgeToEdge()
        setContent {
            val entryId by activeEntryId
            val token by launchToken
            LaunchedEffect(Unit) {
                requestNotificationPermissionIfNeeded()
            }

            LifeLogTheme(darkTheme = false, dynamicColor = false) {
                TimeAuditApp(
                    viewModel = viewModel,
                    initialEntryId = entryId,
                    launchToken = token
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = intent.checkInEntryId()
        activeEntryId.value = id
        if (id != null) {
            // Neuter so rotation doesn't re-trigger the deep link.
            setIntent(Intent(this, MainActivity::class.java))
        } else {
            setIntent(intent)
            // Plain launcher re-open of the existing activity — signal the
            // Compose layer to drop any stale Prompt/entry screen.
            launchToken.intValue++
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemState()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshSystemState() {
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        val exactAlarmAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

        viewModel.refreshSystemState(
            notificationGranted = notificationsGranted,
            exactAlarmAvailable = exactAlarmAvailable
        )
    }

    private fun Intent.checkInEntryId(): String? {
        return if (action == CheckInReceiver.ACTION_CHECK_IN) {
            getStringExtra(CheckInReceiver.EXTRA_ENTRY_ID)
        } else {
            null
        }
    }
}
