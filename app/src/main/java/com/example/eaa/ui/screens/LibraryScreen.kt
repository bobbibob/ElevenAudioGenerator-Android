package com.example.eaa.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eaa.model.GeneratedItem
import com.example.eaa.ui.LibraryRow
import com.example.eaa.ui.SaveFolderChip
import com.example.eaa.util.AudioLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Полноэкранный список сгенерированных MP3. Содержит общий компонент строки
 * [LibraryRow] (имя / прогресс / play-pause / save / delete / rename) и
 * чип выбора папки сохранения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<GeneratedItem>>(emptyList()) }
    var saveFolderLabel by remember { mutableStateOf<String?>(null) }
    var saveInProgressPath by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        items = AudioLibrary.list(context)
        val tree = AudioLibrary.getSaveTree(context)
        saveFolderLabel = tree?.let { AudioLibrary.humanFolderName(context, it) }
    }

    LaunchedEffect(Unit) { refresh() }

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
                title = {
                    Text(
                        "Библиотека (${items.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Чип выбора папки
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SaveFolderChip(
                    folderLabel = saveFolderLabel,
                    onClick = { treePicker.launch(null) }
                )
                if (saveFolderLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        AudioLibrary.setSaveTree(context, null)
                        saveFolderLabel = null
                        Toast.makeText(
                            context,
                            "Сброс: снова Music/ElevenAudioGenerator",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Сбросить") }
                }
            }
            HorizontalDivider()

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Пока нет сгенерированных аудио. Создайте первую запись на главной.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    LibraryRow(
                        item = item,
                        isSaving = saveInProgressPath == item.file.absolutePath,
                        onRefresh = { refresh() },
                        onChooseFolder = { treePicker.launch(null) },
                        onSave = { libItem ->
                            withContext(Dispatchers.IO) {
                                AudioLibrary.exportToUserFolder(context, libItem)
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
