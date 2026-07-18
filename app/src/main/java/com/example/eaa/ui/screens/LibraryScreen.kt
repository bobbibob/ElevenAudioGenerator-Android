package com.example.eaa.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eaa.audio.PlayerHolder
import com.example.eaa.model.GeneratedItem
import com.example.eaa.util.AudioLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран "Библиотека": список сгенерированных MP3 с кнопками
 * Воспроизвести/Пауза, Сохранить в Music/ElevenAudioGenerator, Удалить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(AudioLibrary.list(context)) }
    var playingPath by remember { mutableStateOf(PlayerHolder.current()) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            playingPath = PlayerHolder.current()
            kotlinx.coroutines.delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Библиотека (${items.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Пока нет сгенерированных аудио. Создайте первую запись на главной.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                LibraryRow(
                    item = item,
                    isPlaying = playingPath == item.file.absolutePath && PlayerHolder.isPlaying(),
                    onPlayPause = {
                        PlayerHolder.toggle(
                            item.file,
                            onPrepared = { tick++ },
                            onCompletion = { tick++ }
                        )
                        tick++
                    },
                    onDelete = {
                        if (PlayerHolder.current() == item.file.absolutePath) PlayerHolder.stop()
                        AudioLibrary.remove(context, item)
                        items = AudioLibrary.list(context)
                        Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                    },
                    onSave = {
                        val displayName = item.file.name
                        val saved = AudioLibrary.exportToMusic(context, item.file, displayName)
                        if (saved != null) {
                            Toast.makeText(
                                context,
                                "Сохранено в Music/$saved",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    item: GeneratedItem,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.voiceName, fontWeight = FontWeight.SemiBold)
                Text(
                    "создано: ${df.format(Date(item.createdAt))}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    item.file.absolutePath,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести"
                )
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Сохранить в Music")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    }
}
