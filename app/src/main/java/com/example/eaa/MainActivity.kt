package com.example.eaa

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eaa.api.*
import com.example.eaa.util.KeychainHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val apiService by lazy {
        val logging = HttpLoggingInterceptor { msg -> Log.d(TAG, msg) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("xi-api-key")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ElevenLabsService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppContent() }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun AppContent() {
        val scope = rememberCoroutineScope()
        var apiKey by remember { mutableStateOf(KeychainHelper.get(this) ?: "") }
        var selectedVoice by remember { mutableStateOf<Voice?>(null) }
        var voiceList by remember { mutableStateOf(listOf<Voice>()) }
        var stability by remember { mutableStateOf(0.5) }
        var similarity by remember { mutableStateOf(0.75) }
        var style by remember { mutableStateOf(0.0) }
        var text by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("") }
        var isGenerating by remember { mutableStateOf(false) }
        var isLoadingVoices by remember { mutableStateOf(false) }

        // Дебаунс: сохраняем API-ключ в Keystore не на каждое нажатие, а через 500 мс после паузы
        LaunchedEffect(apiKey) {
            if (apiKey.isNotBlank()) {
                delay(500)
                KeychainHelper.set(this@MainActivity, apiKey)
            }
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Eleven Audio Generator") }) },
            content = { padding ->
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("ElevenLabs API‑key") },
                        singleLine = true
                    )

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
                                        status = friendlyError(e, "Не удалось загрузить голоса")
                                    } finally {
                                        isLoadingVoices = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoadingVoices && apiKey.isNotBlank()
                    ) { Text(if (isLoadingVoices) "Загружаем…" else "Загрузить голоса") }

                    if (voiceList.isNotEmpty()) {
                        DropdownMenuBox(selectedVoice, voiceList) { voice -> selectedVoice = voice }
                    }

                    SliderWithLabel("Stability", stability, 0f..1f) { stability = it }
                    SliderWithLabel("Similarity", similarity, 0f..1f) { similarity = it }
                    SliderWithLabel("Style", style, 0f..1f) { style = it }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Текст главы") },
                        modifier = Modifier.height(120.dp)
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
                                    val savedPath = withContext(Dispatchers.IO) {
                                        val safeName = voice.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                        val outFile = File(
                                            externalCacheDir,
                                            "${safeName}_${System.currentTimeMillis()}.mp3"
                                        )
                                        outFile.writeBytes(body.bytes())
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
                        enabled = !isGenerating && apiKey.isNotBlank() && text.isNotBlank() && selectedVoice != null
                    ) { Text(if (isGenerating) "Генерируем…" else "Сгенерировать") }

                    if (status.isNotEmpty()) Text(status)
                }
            }
        )
    }

    @Composable
    fun SliderWithLabel(label: String, value: Double, range: ClosedFloatingPointRange<Float>, onValueChange: (Double) -> Unit) {
        Column {
            Text("$label: ${"%.2f".format(value)}")
            Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toDouble()) }, valueRange = range)
        }
    }

    @Composable
    fun DropdownMenuBox(selected: Voice?, list: List<Voice>, onSelect: (Voice) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected?.name ?: "Выберите голос")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                list.forEach { voice ->
                    DropdownMenuItem(text = { Text(voice.name) }, onClick = { onSelect(voice); expanded = false })
                }
            }
        }
    }

    /** Превращаем исключения в понятный текст, в том числе тело 4xx/5xx ответа ElevenLabs. */
    private fun friendlyError(e: Exception, prefix: String): String {
        Log.e(TAG, "$prefix: ${e.javaClass.simpleName}: ${e.message}", e)
        if (e is HttpException) {
            val code = e.code()
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val detail = parseElevenLabsDetail(body)
            return "❌ $prefix: HTTP $code${if (detail.isNotBlank()) " — $detail" else ""}"
        }
        return "❌ $prefix: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Вытаскивает message/status из JSON-ответа ElevenLabs вида {"detail": {"status":"...","message":"..."}}. */
    private fun parseElevenLabsDetail(body: String): String {
        if (body.isBlank()) return ""
        // Очень простой парсинг без зависимостей: ищем "message":"..."
        val regex = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
        val match = regex.find(body) ?: return body.take(200)
        return match.groupValues[1].replace("\\n", " ").take(300)
    }

    companion object {
        private const val TAG = "ElevenAudioGen"
    }
}
