package com.example.eaa.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.eaa.api.ElevenLabsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Экран клонирования голоса (Instant Voice Cloning).
 *
 * Пользователь может:
 *  1) записать несколько сэмплов с микрофона
 *  2) выбрать готовые аудио-файлы через SAF
 *  3) ввести имя и описание
 *  4) нажать «Клонировать» — POST /v1/voices/add
 *
 * Новый voice_id добавляется в реестр, обновляется список голосов в UI
 * (экран возвращается на GeneratorScreen, который при перезагрузке подтянет
 * /v1/voices и увидит клона).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneVoiceScreen(
    apiKey: String,
    apiService: ElevenLabsService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isCloning by remember { mutableStateOf(false) }
    val samples = remember { mutableStateListOf<Uri>() }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.stop() } catch (_: Throwable) {}
            try { recorder?.release() } catch (_: Throwable) {}
            try { player?.stop() } catch (_: Throwable) {}
            try { player?.release() } catch (_: Throwable) {}
        }
    }

    val recordPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording(context) { rec, f ->
            recorder = rec
            pendingFile = f
            isRecording = true
        }
        else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_LONG).show()
    }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { samples.add(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клонировать голос", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1) Имя и описание
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя голоса") },
                placeholder = { Text("Например, «Дед Мороз»") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (опц.)") },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 2) Сэмплы
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Сэмплы (1–25 шт., 30с – 3 мин)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Запишите образцы или выберите готовые аудио-файлы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                val perm = Manifest.permission.RECORD_AUDIO
                                if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                                    startRecording(context) { rec, f ->
                                        recorder = rec
                                        pendingFile = f
                                        isRecording = true
                                    }
                                } else {
                                    recordPermLauncher.launch(perm)
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRecording) "Идёт запись…" else "Записать сэмпл")
                        }
                        if (isRecording) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        recorder?.stop()
                                        recorder?.release()
                                    } catch (_: Throwable) {}
                                    recorder = null
                                    isRecording = false
                                    val f = pendingFile
                                    if (f != null && f.exists() && f.length() > 1024) {
                                        samples.add(Uri.fromFile(f))
                                        Toast.makeText(context, "Сэмпл добавлен", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Запись слишком короткая", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Стоп")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { pickAudio.launch(arrayOf("audio/*")) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Выбрать аудио-файлы")
                    }
                    if (samples.isNotEmpty()) {
                        HorizontalDivider()
                        samples.forEachIndexed { idx, uri ->
                            SampleRow(
                                index = idx + 1,
                                uri = uri,
                                isPlaying = playingUri == uri,
                                onPlay = {
                                    try { player?.stop() } catch (_: Throwable) {}
                                    try { player?.release() } catch (_: Throwable) {}
                                    player = MediaPlayer().apply {
                                        setDataSource(context, uri)
                                        setOnPreparedListener { it.start() }
                                        setOnCompletionListener { playingUri = null }
                                        prepareAsync()
                                    }
                                    playingUri = uri
                                },
                                onRemove = {
                                    try { player?.stop() } catch (_: Throwable) {}
                                    try { player?.release() } catch (_: Throwable) {}
                                    player = null
                                    playingUri = null
                                    samples.remove(uri)
                                }
                            )
                        }
                        Text(
                            "Всего: ${samples.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3) Кнопка «Клонировать»
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Введите имя голоса", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (samples.isEmpty()) {
                        Toast.makeText(context, "Добавьте хотя бы один сэмпл", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (apiKey.isBlank()) {
                        Toast.makeText(context, "Сначала укажите API-ключ в настройках", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    isCloning = true
                    scope.launch {
                        try {
                            val nameRb = name.toRequestBody("text/plain".toMediaTypeOrNull())
                            val descRb = description.toRequestBody("text/plain".toMediaTypeOrNull())
                            val labelsRb = "".toRequestBody("text/plain".toMediaTypeOrNull())

                            val parts = withContext(Dispatchers.IO) {
                                samples.mapIndexedNotNull { idx, uri ->
                                    val file = copyUriToCache(context, uri, "sample_$idx") ?: return@mapIndexedNotNull null
                                    val body = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
                                    MultipartBody.Part.createFormData(
                                        "files", file.name, body
                                    )
                                }
                            }
                            if (parts.isEmpty()) {
                                Toast.makeText(context, "Не удалось подготовить файлы", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val resp = apiService.cloneVoice(
                                apiKey = apiKey,
                                name = nameRb,
                                description = descRb,
                                files = parts,
                                labels = labelsRb
                            )
                            Toast.makeText(
                                context,
                                "✅ Голос «${resp.name ?: name}» создан: ${resp.voiceId}",
                                Toast.LENGTH_LONG
                            ).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Ошибка клонирования: ${e.message ?: e.javaClass.simpleName}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isCloning = false
                        }
                    }
                },
                enabled = !isCloning && name.isNotBlank() && samples.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isCloning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Клонируем…")
                } else {
                    Text("Клонировать", fontWeight = FontWeight.SemiBold)
                }
            }

            AssistChip(
                onClick = { /* info */ },
                label = {
                    Text(
                        "ElevenLabs поддерживает мгновенное клонирование (IVC) — голос появится через несколько секунд.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
private fun SampleRow(
    index: Int,
    uri: Uri,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                "Сэмпл $index",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onPlay) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Стоп" else "Воспроизвести"
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.AudioFile,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun startRecording(
    context: android.content.Context,
    onStart: (MediaRecorder, File) -> Unit
) {
    val outDir = context.cacheDir
    val file = File(outDir, "clone_sample_${System.currentTimeMillis()}.m4a")
    @Suppress("DEPRECATION")
    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
    rec.setAudioSource(MediaRecorder.AudioSource.MIC)
    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    rec.setOutputFile(file.absolutePath)
    rec.prepare()
    rec.start()
    onStart(rec, file)
}

private fun copyUriToCache(context: android.content.Context, uri: Uri, base: String): File? {
    return try {
        val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "mp3"
        val out = File(context.cacheDir, "${base}_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out
    } catch (t: Throwable) { null }
}
