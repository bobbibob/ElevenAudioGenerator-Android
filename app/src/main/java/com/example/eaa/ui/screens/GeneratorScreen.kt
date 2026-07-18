package com.example.eaa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.eaa.api.*
import com.example.eaa.model.GeneratedItem
import com.example.eaa.ui.VoiceFilterOptions
import com.example.eaa.ui.VoiceFilters
import com.example.eaa.ui.applyFilters
import com.example.eaa.util.AudioLibrary
import com.example.eaa.util.KeychainHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

/**
 * Главный экран: ввод ключа, выбор голоса с фильтрами, генерация.
 * Результаты смотрим в Библиотеке (кнопка в TopAppBar).
 */
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
    var showFilters by remember { mutableStateOf(false) }

    val options = remember(voiceList) { VoiceFilterOptions.from(voiceList) }
    val filteredVoices = remember(voiceList, filters) { voiceList.applyFilters(filters) }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            kotlinx.coroutines.delay(500)
            KeychainHelper.set(context, apiKey)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eleven Audio Generator") },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Фильтры")
                    }
                    TextButton(onClick = onOpenLibrary) {
                        Text("Библиотека", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("ElevenLabs API‑key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (apiKey.isNotBlank() && !isLoadingVoices) {
                            isLoadingVoices = true
                            status = ""
                            scope.launch {
                                try {
                                    voiceList = apiService.fetchVoices(apiKey).voices
                                    selectedVoice = voiceList.firstOrNull()
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

            if (filteredVoices.isNotEmpty()) {
                VoicePicker(
                    voices = filteredVoices,
                    selected = selectedVoice,
                    onSelect = { selectedVoice = it }
                )
            } else if (voiceList.isNotEmpty()) {
                Text("Ничего не найдено по фильтрам.", style = MaterialTheme.typography.bodySmall)
            }

            if (voiceList.isNotEmpty()) {
                Text(
                    "Выбран: ${selectedVoice?.name ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SliderWithLabel("Stability", stability) { stability = it }
            SliderWithLabel("Similarity", similarity) { similarity = it }
            SliderWithLabel("Style", style) { style = it }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Текст главы") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )

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
                            val savedPath: String = withContext(Dispatchers.IO) {
                                val safeName = voice.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                val outFile = File(
                                    context.externalCacheDir,
                                    "${safeName}_${System.currentTimeMillis()}.mp3"
                                )
                                outFile.writeBytes(body.bytes())
                                AudioLibrary.add(context, outFile, voice.id, voice.name)
                                outFile.absolutePath
                            }
                            status = "✅ Сохранено: $savedPath"
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

            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        }

        if (showFilters) {
            FiltersDialog(
                filters = filters,
                options = options,
                onDismiss = { showFilters = false },
                onApply = { f -> filters = f; showFilters = false },
                onReset = { filters = VoiceFilters() }
            )
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
private fun FiltersDialog(
    filters: VoiceFilters,
    options: VoiceFilterOptions,
    onDismiss: () -> Unit,
    onApply: (VoiceFilters) -> Unit,
    onReset: () -> Unit
) {
    var category by remember { mutableStateOf(filters.category) }
    var gender by remember { mutableStateOf(filters.gender) }
    var age by remember { mutableStateOf(filters.age) }
    var language by remember { mutableStateOf(filters.language) }
    var useCase by remember { mutableStateOf(filters.useCase) }
    var search by remember { mutableStateOf(filters.search) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры голосов") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Поиск") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { ChipPicker("Категория", options.categories, category) { category = it } }
                item { ChipPicker("Пол", options.genders, gender) { gender = it } }
                item { ChipPicker("Возраст", options.ages, age) { age = it } }
                item { ChipPicker("Язык/Акцент", options.languages, language) { language = it } }
                item { ChipPicker("Use case", options.useCases, useCase) { useCase = it } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(VoiceFilters(category, gender, age, language, useCase, search))
            }) { Text("Применить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChipPicker(label: String, values: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        if (values.isEmpty()) {
            Text("— нет данных —", style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text("Любой") }
                )
                values.forEach { v ->
                    FilterChip(
                        selected = selected == v,
                        onClick = { onSelect(v) },
                        label = { Text(v) },
                        leadingIcon = if (selected == v) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
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
