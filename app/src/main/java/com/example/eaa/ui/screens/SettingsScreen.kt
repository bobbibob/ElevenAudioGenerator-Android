package com.example.eaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eaa.util.KeychainHelper

/**
 * Экран «Настройки» — сюда перенесён ввод и хранение ElevenLabs API-ключа.
 *
 * Сам ключ хранится в Android Keystore (см. [KeychainHelper]). Здесь мы только
 * показываем поле, маскируем ввод и обновляем значение, когда пользователь
 * нажимает «Сохранить» (или автоматически через debounce).
 *
 * При удалении ключа мы тоже стираем его из Keystore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onApiKeyChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val initial = remember { KeychainHelper.get(context).orEmpty() }
    var draft by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(initial.isBlank()) }   // если ключ уже был — начнём скрытым
    var saved by remember { mutableStateOf(true) }

    val isValid by remember {
        derivedStateOf { draft.trim().length >= 8 }
    }

    // Авто-сохранение с debounce ~600 мс — как и в старом генераторе.
    LaunchedEffect(draft) {
        snapshotFlow { draft }.collect { current ->
            kotlinx.coroutines.delay(600)
            if (current == draft && current != initial && current.isNotBlank()) {
                KeychainHelper.set(context, current)
                onApiKeyChanged(current)
                saved = true
            } else if (current.isBlank() && initial.isNotBlank()) {
                KeychainHelper.set(context, "")
                onApiKeyChanged("")
                saved = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
            // Заголовок раздела
            SectionHeader(
                icon = Icons.Default.Key,
                title = "ElevenLabs API-ключ",
                subtitle = "Хранится в Android Keystore (AES/GCM), не покидает устройство."
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            saved = false
                        },
                        label = { Text("API-ключ") },
                        placeholder = { Text("xi-api-key…") },
                        singleLine = true,
                        visualTransformation = if (visible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    if (visible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (visible) "Скрыть" else "Показать"
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                if (isValid) "Похоже на корректный ключ (>= 8 символов)."
                                else "Минимум 8 символов.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { /* no-op */ },
                            label = {
                                Text(
                                    if (initial.isBlank()) "Ключ не задан"
                                    else "Ключ сохранён",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        if (!saved) {
                            Text(
                                "Изменения сохраняются автоматически…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (initial.isNotBlank()) {
                            Text(
                                "Всё актуально",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (draft.isNotBlank()) {
                                    KeychainHelper.set(context, draft)
                                    onApiKeyChanged(draft)
                                    saved = true
                                }
                            },
                            enabled = !saved && isValid
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Сохранить")
                        }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                draft = ""
                                KeychainHelper.set(context, "")
                                onApiKeyChanged("")
                                saved = true
                            }
                        ) { Text("Очистить") }
                    }
                }
            }

            // Раздел «Сохранение»
            SectionHeader(
                icon = Icons.Default.Folder,
                title = "Сохранение",
                subtitle = "Где хранить итоговые MP3 — выбирается чипом «Папка» в генераторе и библиотеке."
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "По умолчанию: Music/ElevenAudioGenerator",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Можно выбрать любую папку через системный SAF-пикер — адрес сохранится навсегда.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Раздел «Качество»
            SectionHeader(
                icon = Icons.Default.Speed,
                title = "Качество по умолчанию",
                subtitle = "Эти настройки применяются к новой генерации. Их можно крутить в форме генератора."
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Модель: eleven_multilingual_v2", style = MaterialTheme.typography.bodyMedium)
                    Text("Формат: mp3_44100_128", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Стоимость: ~1 кредит ElevenLabs за символ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Раздел «О приложении»
            SectionHeader(
                icon = Icons.Default.Info,
                title = "О приложении",
                subtitle = null
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Eleven Audio Generator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "v1.0 — генератор аудио-книг через ElevenLabs API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 42.dp)
            )
        }
    }
}
