package com.example.eaa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eaa.api.*
import com.example.eaa.util.KeychainHelper
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : ComponentActivity() {
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/v1/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ElevenLabsService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppContent() }
    }

    @Composable
    fun AppContent() {
        val scope = rememberCoroutineScope()
        var apiKey by remember { mutableStateOf(KeychainHelper.get(this) ?: "") }
        var selectedVoice by remember { mutableStateOf<Voice?>(null) }
        var voiceList by remember { mutableStateOf(listOf<Voice>()) }
        var stability by remember { mutableStateOf(0.75) }
        var similarity by remember { mutableStateOf(0.85) }
        var speed by remember { mutableStateOf(1.0) }
        var pitch by remember { mutableStateOf(0.0) }
        var text by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("") }
        var isGenerating by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Eleven Audio Generator") }) },
            content = { padding ->
                Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // API key entry
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            KeychainHelper.set(this@MainActivity, it)
                        },
                        label = { Text("ElevenLabs API‑key") },
                        singleLine = true
                    )

                    // Load voices button
                    Button(onClick = {
                        if (apiKey.isNotBlank()) {
                            scope.launch {
                                try {
                                    voiceList = apiService.fetchVoices(apiKey).voices
                                    selectedVoice = voiceList.firstOrNull()
                                } catch (e: Exception) { status = "⚠️ ${e.message}" }
                            }
                        }
                    }) { Text("Загрузить голоса") }

                    if (voiceList.isNotEmpty()) {
                        DropdownMenuBox(selectedVoice, voiceList) { voice -> selectedVoice = voice }
                    }

                    // Sliders
                    SliderWithLabel(label = "Stability", value = stability, onValueChange = { stability = it })
                    SliderWithLabel(label = "Similarity", value = similarity, onValueChange = { similarity = it })
                    SliderWithLabel(label = "Speed", value = speed, onValueChange = { speed = it })
                    SliderWithLabel(label = "Pitch", value = pitch, onValueChange = { pitch = it })

                    // Text input
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Текст главы") },
                        modifier = Modifier.height(120.dp)
                    )

                    // Generate button
                    Button(
                        onClick = {
                            val voice = selectedVoice ?: return@Button
                            isGenerating = true
                            status = "Генерация…"
                            scope.launch {
                                try {
                                    val request = SynthesizeRequest(
                                        text = text,
                                        voiceSettings = VoiceSettings(stability, similarity, speed, pitch),
                                        outputFormat = "mp3"
                                    )
                                    val body = apiService.synthesize(voice.id, apiKey, request)
                                    // Save to external files dir
                                    val outFile = File(externalCacheDir, "${voice.name}_${System.currentTimeMillis()}.mp3")
                                    outFile.writeBytes(body.bytes())
                                    status = "✅ Сохранено: ${outFile.absolutePath}"
                                } catch (e: Exception) { status = "❌ ${e.message}" }
                                isGenerating = false
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
    fun SliderWithLabel(label: String, value: Double, onValueChange: (Double) -> Unit) {
        Column {
            Text("$label: ${"%.2f".format(value)}")
            Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toDouble()) }, valueRange = 0f..1f)
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
                    DropdownMenuItem(onClick = { onSelect(voice); expanded = false }) {
                        Text(voice.name)
                    }
                }
            }
        }
    }
}
