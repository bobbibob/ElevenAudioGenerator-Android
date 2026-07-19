package com.example.eaa.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eaa.api.*
import com.example.eaa.api.SubscriptionResponse
import com.example.eaa.audio.PlayerHolder
import com.example.eaa.model.GeneratedItem
import com.example.eaa.ui.LibraryRow
import com.example.eaa.ui.SaveFolderChip
import com.example.eaa.ui.VoiceFilterOptions
import com.example.eaa.ui.VoiceFilters
import com.example.eaa.ui.applyFilters
import com.example.eaa.ui.filterDisplay
import com.example.eaa.ui.toLike
import com.example.eaa.util.AudioLibrary
import com.example.eaa.util.Chunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    apiKey: String,
    modelId: String = "eleven_multilingual_v2",
    apiService: ElevenLabsService,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedVoice by remember { mutableStateOf<com.example.eaa.ui.VoiceLike?>(null) }
    var voiceList by remember { mutableStateOf(listOf<Voice>()) }
    var sharedVoices by remember { mutableStateOf(listOf<com.example.eaa.api.SharedVoice>()) }
    var stability by remember { mutableStateOf(0.5) }
    var similarity by remember { mutableStateOf(0.75) }
    var style by remember { mutableStateOf(0.0) }
    // Расширенные параметры (по умолчанию — нейтральные)
    var speed by remember { mutableStateOf(1.0) }
    var pitch by remember { mutableStateOf(0.0) }
    var speakerBoost by remember { mutableStateOf(true) }
    var seedEnabled by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf("0") }
    var usePrevNext by remember { mutableStateOf(false) }
    var prevText by remember { mutableStateOf("") }
    var nextText by remember { mutableStateOf("") }
    var outputFormat by remember { mutableStateOf("mp3_44100_128") }
    // Раскрытие секции «Дополнительно»
    var advancedOpen by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var audioTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingVoices by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(VoiceFilters()) }
    var subscription by remember { mutableStateOf<SubscriptionResponse?>(null) }
    var isLoadingBalance by remember { mutableStateOf(false) }

    val options = remember(voiceList, sharedVoices) { VoiceFilterOptions.from(voiceList, sharedVoices) }
    val filteredVoices = remember(voiceList, sharedVoices, filters) {
        // Для языка ru подмешиваем локальный каталог (≈50 премиум-голосов ElevenLabs Voice Library)
        val ruCatalog = com.example.eaa.util.RuVoiceCatalog.forFilter(filters.language)
        val sources: List<com.example.eaa.ui.VoiceLike> = buildList {
            voiceList.forEach { add(it.toLike()) }
            sharedVoices.forEach { add(it.toLike()) }
            ruCatalog?.forEach { add(it.toLike()) }
        }
        sources.applyFilters(filters)
    }

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
    fun refreshBalance() {
        if (apiKey.isBlank() || isLoadingBalance) return
        isLoadingBalance = true
        scope.launch {
            try {
                subscription = apiService.fetchSubscription(apiKey)
            } catch (_: Exception) {
                subscription = null
            } finally {
                isLoadingBalance = false
            }
        }
    }

    LaunchedEffect(refreshTick) { refresh() }
    LaunchedEffect(apiKey) { if (apiKey.isNotBlank()) refreshBalance() }

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
                navigationIcon = {
                    BalanceChip(
                        subscription = subscription,
                        isLoading = isLoadingBalance,
                        onRefresh = { refreshBalance() }
                    )
                },
                title = {
                    Text(
                        "Eleven Audio",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Библиотека"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Настройки"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Если API-ключ не задан — красивый баннер
            if (apiKey.isBlank()) {
                item {
                    ApiKeyMissingCard(onOpenSettings = onOpenSettings)
                }
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
                                        val own = apiService.fetchVoices(apiKey).voices
                                        val shared = runCatching {
                                            apiService.fetchSharedVoices(apiKey, pageSize = 200, page = 0).voices
                                        }.getOrDefault(emptyList())
                                        voiceList = own
                                        sharedVoices = shared
                                        selectedVoice = voiceList.firstOrNull()?.toLike()
                                    } catch (e: Exception) {
                                        status = friendlyError(e, "Голоса не загрузились")
                                    } finally {
                                        isLoadingVoices = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoadingVoices && apiKey.isNotBlank()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLoadingVoices) "Загружаем…" else "Загрузить голоса")
                    }

                    Spacer(Modifier.width(12.dp))
                    if (voiceList.isNotEmpty()) {
                        Text(
                            "${filteredVoices.size}/${voiceList.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- FILTERS ---
            if (voiceList.isNotEmpty()) {
                item {
                    Text(
                        "Фильтры",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                        onSelect = { v -> selectedVoice = v }
                    )
                }
            } else if (voiceList.isNotEmpty() || sharedVoices.isNotEmpty()) {
                item {
                    Text(
                        "Ничего не найдено по фильтрам.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- VOICE SETTINGS ---
            if (selectedVoice != null) {
                item {
                    Text(
                        "Параметры голоса",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item { SliderWithLabel("Stability", stability) { stability = it } }
                item { SliderWithLabel("Similarity", similarity) { similarity = it } }
                item { SliderWithLabel("Style", style) { style = it } }
                item { SliderWithLabel("Speed (скорость)", speed, 0.5, 2.0) { speed = it } }
                item { SliderWithLabel("Pitch (высота)", pitch, -1.0, 1.0) { pitch = it } }

                item {
                    AdvancedSettingsCard(
                        speakerBoost = speakerBoost, onSpeakerBoost = { speakerBoost = it },
                        seedEnabled = seedEnabled, onSeedEnabled = { seedEnabled = it },
                        seed = seed, onSeed = { seed = it },
                        usePrevNext = usePrevNext, onUsePrevNext = { usePrevNext = it },
                        prevText = prevText, onPrevText = { prevText = it },
                        nextText = nextText, onNextText = { nextText = it },
                        outputFormat = outputFormat, onOutputFormat = { outputFormat = it },
                        expanded = advancedOpen, onExpandToggle = { advancedOpen = !advancedOpen }
                    )
                }
            }

            // --- TITLE + TEXT + GENERATE ---
            item {
                OutlinedTextField(
                    value = audioTitle,
                    onValueChange = { audioTitle = it },
                    label = { Text("Название аудио (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст главы") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
            // Мини-сводка: символов → примерная стоимость
            item {
                GenerationSummary(text = text, modelId = modelId)
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
                                    modelId = modelId,
                                    voiceSettings = VoiceSettings(
                                        stability = stability,
                                        similarityBoost = similarity,
                                        style = style,
                                        useSpeakerBoost = speakerBoost,
                                        speed = speed,
                                        pitch = pitch
                                    ),
                                    seed = seedEnabled.toLongOrNullSafely(),
                                    previousText = if (usePrevNext && prevText.isNotBlank()) prevText else null,
                                    nextText = if (usePrevNext && nextText.isNotBlank()) nextText else null
                                )
                                val chunks = Chunker.split(text, maxChars = 4500)
                                val total = chunks.size
                                val totalChars = text.trim().length
                                val cost = AudioLibrary.estimateCostCredits(totalChars, modelId)
                                val outFile = withContext(Dispatchers.IO) {
                                    val safeName = AudioLibrary.sanitizeFileName(voice.name)
                                    val out = File(
                                        context.externalCacheDir,
                                        "${safeName}_${System.currentTimeMillis()}.mp3"
                                    )
                                    out.outputStream().use { sink ->
                                        chunks.forEachIndexed { i, chunk ->
                                            if (total > 1) {
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    status = "Генерация… ${i + 1}/$total"
                                                }
                                            }
                                            val part = apiService.synthesize(
                                                voiceId = voice.id,
                                                apiKey = apiKey,
                                                outputFormat = outputFormat,
                                                request = request.copy(text = chunk)
                                            )
                                            part.byteStream().use { it.copyTo(sink) }
                                        }
                                    }
                                    out
                                }
                                val display = audioTitle.trim()
                                withContext(Dispatchers.IO) {
                                    AudioLibrary.add(
                                        context, outFile, voice.id, voice.name,
                                        displayName = display,
                                        characterCount = totalChars,
                                        chunkCount = total,
                                        costCredits = cost
                                    )
                                }
                                refreshTick++
                                audioTitle = ""
                                refreshBalance()
                                status = if (total > 1) "✅ Готово (склеено $total частей)" else "✅ Готово"
                            } catch (e: Exception) {
                                status = friendlyError(e, "Ошибка генерации")
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating && apiKey.isNotBlank() && text.isNotBlank() && selectedVoice != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (isGenerating) "Генерируем…" else "Сгенерировать",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (status.isNotEmpty()) {
                item {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        fontWeight = FontWeight.SemiBold,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun GenerationSummary(text: String, modelId: String) {
    val chars = text.trim().length
    val cost = AudioLibrary.estimateCostCredits(chars, modelId)
    val chunks = Chunker.split(text, maxChars = 4500).size
    val canGenerate = chars > 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatPill("символов", formatThousands(chars), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatPill(
                "примерно",
                "~$cost кр.",
                Modifier.weight(1f),
                highlight = canGenerate,
                highlightColor = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(8.dp))
            StatPill("частей", chunks.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlight) highlightColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) highlightColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatThousands(n: Int): String =
    "%,d".format(java.util.Locale.US, n).replace(',', ' ')

@Composable
private fun ApiKeyMissingCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "API-ключ не задан",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "Чтобы генерировать аудио, откройте «Настройки» (шестерёнка справа сверху) и сохраните свой ElevenLabs API-ключ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Открыть настройки")
            }
        }
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Double,
    min: Double = 0.0,
    max: Double = 1.0,
    onValueChange: (Double) -> Unit
) {
    Column {
        Row {
            Text(
                "$label:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "%.2f".format(value),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    voices: List<com.example.eaa.ui.VoiceLike>,
    selected: com.example.eaa.ui.VoiceLike?,
    onSelect: (com.example.eaa.ui.VoiceLike) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var previewingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(previewingId) {
        val id = previewingId ?: return@LaunchedEffect
        val v = voices.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val url = v.previewUrl ?: return@LaunchedEffect
        PlayerHolder.stop()
        scope.launch {
            val tmp = withContext(Dispatchers.IO) {
                runCatching {
                    val client = okhttp3.OkHttpClient()
                    val resp = client.newCall(
                        okhttp3.Request.Builder().url(url).build()
                    ).execute()
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body ?: return@runCatching null
                    val ext = url.substringAfterLast('.').take(4).ifBlank { "mp3" }
                    val f = File.createTempFile("preview_", ".$ext", context.cacheDir)
                    f.outputStream().use { body.byteStream().copyTo(it) }
                    f
                }.getOrNull()
            }
            if (tmp != null) {
                PlayerHolder.toggle(tmp, onCompletion = { previewingId = null })
            } else {
                previewingId = null
            }
        }
    }

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
                val hasPreview = !voice.previewUrl.isNullOrBlank()
                val isPreviewing = previewingId == voice.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(voice.name)
                                val sub = listOfNotNull(
                                    voice.category,
                                    voice.label("gender"),
                                    voice.label("accent"),
                                    voice.label("language")
                                ).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall)
                            }
                            if (hasPreview) {
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (isPreviewing) {
                                            PlayerHolder.stop()
                                            previewingId = null
                                        } else {
                                            previewingId = voice.id
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (isPreviewing) Icons.Default.Stop
                                        else Icons.Default.PlayArrow,
                                        contentDescription = if (isPreviewing) "Стоп" else "Превью"
                                    )
                                }
                            }
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

/**
 * Маленький чип в левом углу TopAppBar с балансом ElevenLabs.
 *
 * Показывает «N кр.» зелёным (или янтарным, если запас мал). Тап — обновляет.
 * Если баланс ещё не загружен или API-ключ не задан — показываем «•••».
 */
@Composable
private fun BalanceChip(
    subscription: SubscriptionResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val text = when {
        isLoading && subscription == null -> "•••"
        subscription == null -> "—"
        else -> {
            val credits = subscription.characterCount ?: 0
            val usd = com.example.eaa.util.AudioLibrary.creditsToDollars(credits)
            "$" + String.format(java.util.Locale.ROOT, "%.2f", usd)
        }
    }
    val remaining = subscription?.characterCount ?: 0
    val lowBalance = subscription != null && remaining in 1..999
    val containerColor = when {
        subscription == null -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
        lowBalance -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AttachMoney,
            contentDescription = "Баланс",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(2.dp))
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Обновить баланс",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Вспомогательное расширение — Boolean.toLongOrNullSafely */
private fun Boolean.toLongOrNullSafely(): Long? = if (this) 0L else null

/**
 * Раскрывающаяся карточка «Дополнительно»: speaker boost, seed,
 * previous/next text, формат вывода.
 */
@Composable
private fun AdvancedSettingsCard(
    speakerBoost: Boolean,
    onSpeakerBoost: (Boolean) -> Unit,
    seedEnabled: Boolean,
    onSeedEnabled: (Boolean) -> Unit,
    seed: String,
    onSeed: (String) -> Unit,
    usePrevNext: Boolean,
    onUsePrevNext: (Boolean) -> Unit,
    prevText: String,
    onPrevText: (String) -> Unit,
    nextText: String,
    onNextText: (String) -> Unit,
    outputFormat: String,
    onOutputFormat: (String) -> Unit,
    expanded: Boolean,
    onExpandToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Дополнительно",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Speaker boost, seed, previous/next, формат",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть"
                    )
                }
            }
            if (expanded) {
                HorizontalDivider()

                // Speaker boost
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Speaker boost", fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(
                            "Усиливает сходство с оригинальным голосом",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = speakerBoost,
                            onCheckedChange = onSpeakerBoost
                        )
                    }
                )

                // Seed
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Фиксированный seed", fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(
                            "Один и тот же текст+seed дают одинаковый голос",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = seedEnabled,
                            onCheckedChange = onSeedEnabled
                        )
                    }
                )
                if (seedEnabled) {
                    OutlinedTextField(
                        value = seed,
                        onValueChange = onSeed,
                        label = { Text("Seed (целое число)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Previous/next
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Previous / next text", fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(
                            "Контекст для более естественной интонации",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = usePrevNext,
                            onCheckedChange = onUsePrevNext
                        )
                    }
                )
                if (usePrevNext) {
                    OutlinedTextField(
                        value = prevText,
                        onValueChange = onPrevText,
                        label = { Text("Предыдущий текст (опц.)") },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                    OutlinedTextField(
                        value = nextText,
                        onValueChange = onNextText,
                        label = { Text("Следующий текст (опц.)") },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }

                // Формат вывода
                Text(
                    "Формат вывода",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                val formats = com.example.eaa.api.OutputFormats.ALL
                formats.forEach { f ->
                    FormatRow(
                        option = f,
                        selected = outputFormat == f.id,
                        onSelect = { onOutputFormat(f.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatRow(
    option: com.example.eaa.api.OutputFormats.Option,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                option.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
