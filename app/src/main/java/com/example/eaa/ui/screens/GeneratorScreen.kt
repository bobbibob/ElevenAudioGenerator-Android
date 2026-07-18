package com.example.eaa.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.eaa.api.*
import com.example.eaa.model.GeneratedItem
import com.example.eaa.ui.LibraryRow
import com.example.eaa.ui.SaveFolderChip
import com.example.eaa.ui.VoiceFilterOptions
import com.example.eaa.ui.VoiceFilters
import com.example.eaa.ui.applyFilters
import com.example.eaa.ui.filterDisplay
import com.example.eaa.util.AudioLibrary
import com.example.eaa.util.KeychainHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    apiService: ElevenLabsService,
    onOpenLibrary: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(KeychainHelper.get(context) ?: "") }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var voiceList by remember { mutableStateOf(listOf<Voice>()) }
    var stability by remember { mutableStateOf(0.5) }
    var similarity by remember { mutableStateOf(0.75) }
    var style by remember { mutableStateOf(0.0) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingVoices by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(VoiceFilters()) }

    val options = remember(voiceList) { VoiceFilterOptions.from(voiceList) }
    val filteredVoices = remember(voiceList, filters) { voiceList.applyFilters(filters) }

    // Состояние библиотеки (прогресс / список) — на главном экране
    var refreshTick by remember { mutableStateOf(0) }
    var libraryItems by remember { mutableStateOf<List<GeneratedItem>>(emptyList()) }
    var saveFolderLabel by remember { mutableStateOf<String?>(null) }
    var saveInProgressPath by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        libraryItems = AudioLibrary.list(context)
        val tree = AudioLibrary.getSaveTree(context)
        saveFolderLabel = tree?.let { AudioLibrary.humanFolderName(context, it) }
    }
    LaunchedEffect(refreshTick) { refresh() }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            kotlinx.coroutines.delay(500)
            KeychainHelper.set(context, apiKey)
        }
    }

    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            AudioLibrary.setSaveTree(context, uri)
            saveFolderLabel = AudioLibrary.humanFolderName(context, uri)
            Toast.makeText(context, "Папка сохранения обновлена", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eleven Audio Generator") },
                actions = {
                    TextButton(onClick = onOpenLibrary) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Библиотека", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- API KEY ---
            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("ElevenLabs API‑key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- LOAD VOICES ---
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (apiKey.isNotBlank() && !isLoadingVoices) {
                                isLoadingVoices = true
                                status = ""
                                scope.launch {
                                    try {
                                        voiceList = apiService.fetchVoices(apiKey).voices
                                        selectedVoice = filteredVoices.firstOrNull() ?: voiceList.firstOrNull()
                                    } catch (e: Exception) {
                                        status = friendlyError(e, "Голоса не загрузились")
                                    } finally {
                                        isLoadingVoices = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoadingVoices && apiKey.isNotBlank()
                    ) { Text(if (isLoadingVoices) "Загружаем…" else "Загрузить голоса") }

                    Spacer(Modifier.width(12.dp))
                    if (voiceList.isNotEmpty()) {
                        Text(
                            "${filteredVoices.size}/${voiceList.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // --- FILTERS ---
            if (voiceList.isNotEmpty()) {
                item { Text("Фильтры", style = MaterialTheme.typography.titleMedium) }
                item {
                    FilterDropdown("Категория", options.categories, filters.category) {
                        filters = filters.copy(category = it)
                    }
                }
                item {
                    FilterDropdown("Пол", options.genders, filters.gender) {
                        filters = filters.copy(gender = it)
                    }
                }
                item {
                    FilterDropdown("Возраст", options.ages, filters.age) {
                        filters = filters.copy(age = it)
                    }
                }
                item {
                    FilterDropdown("Язык / Акцент", options.languages, filters.language) {
                        filters = filters.copy(language = it)
                    }
                }
                item {
                    FilterDropdown("Применение (use case)", options.useCases, filters.useCase) {
                        filters = filters.copy(useCase = it)
                    }
                }
                item {
                    OutlinedTextField(
                        value = filters.search,
                        onValueChange = { filters = filters.copy(search = it) },
                        label = { Text("Поиск по имени / описанию") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (filters.activeCount() > 0) {
                    item {
                        AssistChip(
                            onClick = { filters = VoiceFilters() },
                            label = { Text("Сбросить все фильтры (${filters.activeCount()})") }
                        )
                    }
                }
            }

            // --- VOICE PICKER ---
            if (filteredVoices.isNotEmpty()) {
                item {
                    VoicePicker(
                        voices = filteredVoices,
                        selected = selectedVoice,
                        onSelect = { selectedVoice = it }
                    )
                }
            } else if (voiceList.isNotEmpty()) {
                item {
                    Text("Ничего не найдено по фильтрам.", style = MaterialTheme.typography.bodySmall)
                }
            }

            // --- VOICE SETTINGS ---
            item { SliderWithLabel("Stability", stability) { stability = it } }
            item { SliderWithLabel("Similarity", similarity) { similarity = it } }
            item { SliderWithLabel("Style", style) { style = it } }

            // --- TEXT + GENERATE ---
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст главы") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            }
            item {
                Button(
                    onClick = {
                        val voice = selectedVoice ?: return@Button
                        isGenerating = true
                        status = "Генерация…"
                        scope.launch {
                            try {
                                val request = SynthesizeRequest(
                                    text = text,
                                    modelId = "eleven_multilingual_v2",
                                    voiceSettings = VoiceSettings(
                                        stability = stability,
                                        similarityBoost = similarity,
                                        style = style,
                                        useSpeakerBoost = true
                                    )
                                )
                                val body = apiService.synthesize(
                                    voiceId = voice.id,
                                    apiKey = apiKey,
                                    outputFormat = "mp3_44100_128",
                                    request = request
                                )
                                withContext(Dispatchers.IO) {
                                    val safeName = AudioLibrary.sanitizeFileName(voice.name)
                                    val outFile = File(
                                        context.externalCacheDir,
                                        "${safeName}_${System.currentTimeMillis()}.mp3"
                                    )
                                    outFile.writeBytes(body.bytes())
                                    AudioLibrary.add(context, outFile, voice.id, voice.name)
                                }
                                refreshTick++
                                status = "✅ Готово"
                            } catch (e: Exception) {
                                status = friendlyError(e, "Ошибка генерации")
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating && apiKey.isNotBlank() && text.isNotBlank() && selectedVoice != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isGenerating) "Генерируем…" else "Сгенерировать") }
            }
            if (status.isNotEmpty()) {
                item { Text(status, style = MaterialTheme.typography.bodySmall) }
            }

            // --- SAVE FOLDER + LIBRARY (под формой) ---
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Сгенерированные аудио",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    SaveFolderChip(
                        folderLabel = saveFolderLabel,
                        onClick = { treePicker.launch(null) }
                    )
                }
            }

            if (libraryItems.isEmpty()) {
                item {
                    Text(
                        "Пока нет аудио. Нажмите «Сгенерировать», чтобы создать первый файл.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(libraryItems, key = { it.id }) { libItem ->
                    LibraryRow(
                        item = libItem,
                        isSaving = saveInProgressPath == libItem.file.absolutePath,
                        onRefresh = { refreshTick++ },
                        onChooseFolder = { treePicker.launch(null) },
                        onSave = { i ->
                            withContext(Dispatchers.IO) {
                                AudioLibrary.exportToUserFolder(context, i)
                            }
                        },
                        onSaved = { path ->
                            Toast.makeText(
                                context,
                                "Сохранено: $path",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderWithLabel(label: String, value: Double, onValueChange: (Double) -> Unit) {
    Column {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toDouble()) }, valueRange = 0f..1f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(voices: List<Voice>, selected: Voice?, onSelect: (Voice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Выберите голос",
            onValueChange = {},
            readOnly = true,
            label = { Text("Голос") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(voice.name)
                            val sub = listOfNotNull(
                                voice.category,
                                voice.label("gender"),
                                voice.label("accent"),
                                voice.label("language")
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = { onSelect(voice); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = filterDisplay(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Любой") },
                onClick = { onSelect(null); expanded = false }
            )
            HorizontalDivider()
            options.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected == v) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(v.replaceFirstChar { it.uppercase() })
                        }
                    },
                    onClick = { onSelect(v); expanded = false }
                )
            }
        }
    }
}

private fun friendlyError(e: Exception, prefix: String): String {
    if (e is HttpException) {
        val code = e.code()
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val msg = runCatching {
            Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        }.getOrNull().orEmpty()
        return "❌ $prefix: HTTP $code" + if (msg.isNotBlank()) " — $msg" else ""
    }
    return "❌ $prefix: ${e.message ?: e.javaClass.simpleName}"
}
