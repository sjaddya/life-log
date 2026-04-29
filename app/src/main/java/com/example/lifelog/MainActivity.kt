package com.example.lifelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.lifelog.ui.theme.LifeLogTheme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.room.RoomDatabase

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.lifelog.data.local.AppDatabase
import com.example.lifelog.data.local.entity.Entry
import com.example.lifelog.data.local.dao.EntryDao

lateinit var db: AppDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "lifelog-db"
        ).build()

        enableEdgeToEdge()
        setContent {
            LifeLogTheme {
                MainScreen()
            }
        }
    }
}

fun saveEntry(text: String) {
    CoroutineScope(Dispatchers.IO).launch {

        val entry = Entry(
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            text = text,
            status = "completed"
        )

        db.entryDao().insert(entry)
    }
}

@Composable
fun MainScreen() {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What did you do?") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            saveEntry(text)
            text = ""
        }) {
            Text("Save")
        }

        Button(onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                val entries = db.entryDao().getAll()
                android.util.Log.d("DB_LIST", entries.toString())
            }
        }) {
            Text("Show Entries")
        }
    }
}