package com.example.lifelog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifelog.data.local.AppDatabase
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.domain.EntrySource
import com.example.lifelog.domain.EntryStatus
import com.example.lifelog.domain.IntervalGenerator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimeAuditRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repo: TimeAuditRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        repo = TimeAuditRepository(db, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(
        id: String,
        status: EntryStatus = EntryStatus.Pending,
        endTime: Long = 1_000L,
        text: String? = null,
        audioPath: String? = null,
        filledAt: Long? = null
    ) = Entry(
        id = id,
        date = IntervalGenerator.todayKey(),
        startTime = 0L,
        endTime = endTime,
        text = text,
        audioPath = audioPath,
        status = status,
        source = null,
        createdAt = 0L,
        filledAt = filledAt
    )

    @Test
    fun saveText_onPendingEntry_marksCompletedAndTrims() = runTest {
        db.entryDao().insert(entry("e1", EntryStatus.Pending))
        repo.saveText("e1", "  walked the dog  ")
        val updated = db.entryDao().getById("e1")!!
        assertEquals(EntryStatus.Completed, updated.status)
        assertEquals("walked the dog", updated.text)
        assertEquals(EntrySource.Manual, updated.source)
        assertFalse(updated.isEdited)
    }

    @Test
    fun saveText_onMissedEntry_marksBackfilled() = runTest {
        db.entryDao().insert(entry("e2", EntryStatus.Missed))
        repo.saveText("e2", "lunch")
        assertEquals(EntryStatus.Backfilled, db.entryDao().getById("e2")!!.status)
    }

    @Test
    fun saveText_onAlreadyFilledEntry_setsIsEdited() = runTest {
        db.entryDao().insert(entry("e3", EntryStatus.Completed, filledAt = 123L))
        repo.saveText("e3", "revised")
        assertTrue(db.entryDao().getById("e3")!!.isEdited)
    }

    @Test
    fun saveText_clearsExistingAudio() = runTest {
        db.entryDao().insert(entry("e4", EntryStatus.Completed, audioPath = "/tmp/x.m4a"))
        repo.saveText("e4", "now a text entry")
        assertNull(db.entryDao().getById("e4")!!.audioPath)
    }

    @Test
    fun attachAudio_onPending_marksCompletedWithVoiceSource() = runTest {
        db.entryDao().insert(entry("e5", EntryStatus.Pending))
        repo.attachAudio("e5", "/tmp/rec.m4a")
        val updated = db.entryDao().getById("e5")!!
        assertEquals(EntryStatus.Completed, updated.status)
        assertEquals("/tmp/rec.m4a", updated.audioPath)
        assertEquals(EntrySource.VoiceRecording, updated.source)
    }

    @Test
    fun skip_marksSkipped() = runTest {
        db.entryDao().insert(entry("e6", EntryStatus.Pending))
        repo.skip("e6")
        assertEquals(EntryStatus.Skipped, db.entryDao().getById("e6")!!.status)
    }

    @Test
    fun remindLater_resetsToPending() = runTest {
        db.entryDao().insert(entry("e7", EntryStatus.Missed))
        repo.remindLater("e7")
        assertEquals(EntryStatus.Pending, db.entryDao().getById("e7")!!.status)
    }

    @Test
    fun markPastPendingMissed_flipsOnlyOverduePendingEntries() = runTest {
        val now = System.currentTimeMillis()
        db.entryDao().insert(entry("past", EntryStatus.Pending, endTime = now - 10_000))
        db.entryDao().insert(entry("future", EntryStatus.Pending, endTime = now + 10_000))
        repo.markPastPendingMissed()
        assertEquals(EntryStatus.Missed, db.entryDao().getById("past")!!.status)
        assertEquals(EntryStatus.Pending, db.entryDao().getById("future")!!.status)
    }

    @Test
    fun ensureTodayExists_generatesOnceAndIsIdempotent() = runTest {
        settings.saveDaySetup(wakeMinutes = 8 * 60, endMinutes = 12 * 60, intervalMinutes = 60)
        repo.ensureTodayExists()
        val firstCount = repo.todayEntriesOnce().size
        assertTrue(firstCount > 0)
        repo.ensureTodayExists()
        assertEquals(firstCount, repo.todayEntriesOnce().size)
    }
}
