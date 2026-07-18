package com.example.eaa.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.eaa.audio.PlayerHolder
import com.example.eaa.audio.rememberPlayerProgress
import com.example.eaa.model.GeneratedItem
import com.example.eaa.util.AudioLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Общая строка библиотеки: имя (можно редактировать), шкала прогресса,
 * кнопки «Воспроизвести/Пауза», «Сохранить» (в выбранную папку), «Удалить»,
 * «Изменить имя», «Выбрать папку».
 *
 * Параметры:
 *  - [item] — запись библиотеки
 *  - [isSaving] — внешнее состояние «сейчас сохраняется» (для спиннера на кнопке)
 *  - [saveFolderLabel] — что показывать на чипе выбора папки
 *  - [onRefresh] — вызывающий код должен перечитать список (после удаления/сохранения)
 *  - [onChooseFolder] — вызывающий код откроет системный документ-пикер
 *  - [onSave] — вызывающий код делает сохранение и возвращает имя через [onSaved] для тоста
 *  - [onSaved] — опционально, строка результата (для тоста)
 */
@Composable
fun LibraryRow(
    item: GeneratedItem,
    isSaving: Boolean = false,
    saveFolderLabel: String? = null,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit = {},
    onSave: suspend (GeneratedItem) -> String?,
    onSaved: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    val isThisPath = PlayerHolder.current() == item.file.absolutePath
    val isThisPlaying = isThisPath && PlayerHolder.isPlaying()
    val duration = if (isThisPath) PlayerHolder.durationMs.value else 0
    val position = if (isThisPath) PlayerHolder.positionMs.value else 0

    // Подписка на прогресс — пока строка «живая» и трек играет, обновляем позицию.
    rememberPlayerProgress(item.file.absolutePath)
    // Чтобы Slider реагировал на смену трека (а не только на тики):
    val rev = PlayerHolder.revision.value

    var editing by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf(item.displayName) }

    val title = remember(item.displayName, item.voiceName) {
        AudioLibrary.visibleName(item)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            // Заголовок: видимое имя + иконка «редактировать»
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (item.displayName.isNotBlank())
                            "голос: ${item.voiceName}"
                        else
                            "создано: ${df.format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = {
                    draftName = item.displayName.ifBlank { item.voiceName }
                    editing = true
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Переименовать")
                }
            }

            // Шкала прогресса
            if (duration > 0) {
                val dur = duration.coerceAtLeast(1).toFloat()
                Slider(
                    value = position.toFloat().coerceIn(0f, dur),
                    onValueChange = { newPos ->
                        if (isThisPath) PlayerHolder.seekTo(newPos.toInt())
                    },
                    valueRange = 0f..dur,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatMs(position), style = MaterialTheme.typography.labelSmall)
                    Text(text = formatMs(duration), style = MaterialTheme.typography.labelSmall)
                }
            } else if (isThisPath) {
                // MediaPlayer ещё не отдал duration
                Text(
                    "…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                // Трек не открыт — покажем «длительность неизвестна» в виде «--:-- / --:--»
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "--:--", style = MaterialTheme.typography.labelSmall)
                    Text(text = "--:--", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Кнопки действий
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        PlayerHolder.toggle(
                            item.file,
                            seekToMs = if (isThisPath) PlayerHolder.position() else 0
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isThisPlaying) "Пауза" else "Play")
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = {
                        if (!isSaving) {
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    onSave(item)
                                }
                                if (saved != null) onSaved(saved) else
                                    Toast.makeText(
                                        context,
                                        "Не удалось сохранить",
                                        Toast.LENGTH_LONG
                                    ).show()
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSaving) "…" else "Сохранить")
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = {
                        if (PlayerHolder.current() == item.file.absolutePath) PlayerHolder.stop()
                        AudioLibrary.remove(context, item)
                        onRefresh()
                        Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Удалить")
                }
            }

            // Чтобы строка рекомпозировалась, когда меняется rev
            @Suppress("UNUSED_EXPRESSION") rev
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Название файла") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        singleLine = true,
                        label = { Text("Имя (без .mp3)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Пусто — будет использоваться «${item.voiceName}»",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sanitized = AudioLibrary.sanitizeFileName(draftName)
                    AudioLibrary.setDisplayName(context, item, sanitized)
                    editing = false
                    onRefresh()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Отмена") }
            }
        )
    }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

/** Чип-индикатор выбранной папки — общий компонент. */
@Composable
fun SaveFolderChip(
    folderLabel: String?,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                if (folderLabel.isNullOrBlank()) "Папка: не выбрана"
                else "Папка: $folderLabel",
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    )
}
