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

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.lifelog.data.local.AppDatabase

lateinit var db: AppDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "lifelog-db"
        ).build()

        CoroutineScope(Dispatchers.IO).launch {

            val entry = com.example.lifelog.data.local.entity.Entry(
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis() + 1000,
                text = "First test entry",
                status = "completed"
            )

            db.entryDao().insert(entry)

            val entries = db.entryDao().getAll()

            println("DB_TEST: $entries")
        }

        enableEdgeToEdge()
        setContent {
            LifeLogTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LifeLogTheme {
        Greeting("Android")
    }
}