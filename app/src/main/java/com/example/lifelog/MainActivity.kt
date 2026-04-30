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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.lifelog.data.SettingsRepository
import com.example.lifelog.data.TimeAuditRepository
import com.example.lifelog.data.local.AppDatabase
import com.example.lifelog.notifications.CheckInReceiver
import com.example.lifelog.notifications.CheckInScheduler
import com.example.lifelog.ui.TimeAuditApp
import com.example.lifelog.ui.theme.LifeLogTheme
import com.example.lifelog.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val activeEntryId = mutableStateOf<String?>(null)
    private lateinit var viewModel: MainViewModel
    private lateinit var scheduler: CheckInScheduler

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshSystemState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeEntryId.value = intent.checkInEntryId()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "lifelog-db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val settingsRepository = SettingsRepository(applicationContext)
        val repository = TimeAuditRepository(db, settingsRepository)
        scheduler = CheckInScheduler(applicationContext)
        viewModel = MainViewModel(repository, scheduler)

        enableEdgeToEdge()
        setContent {
            val entryId by activeEntryId
            LaunchedEffect(Unit) {
                refreshSystemState()
                requestNotificationPermissionIfNeeded()
            }

            LifeLogTheme(dynamicColor = false) {
                TimeAuditApp(viewModel = viewModel, initialEntryId = entryId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeEntryId.value = intent.checkInEntryId()
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
