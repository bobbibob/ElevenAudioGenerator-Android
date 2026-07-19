package com.example.eaa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.eaa.api.ElevenLabsService
import com.example.eaa.audio.PlayerHolder
import com.example.eaa.ui.screens.GeneratorScreen
import com.example.eaa.ui.screens.LibraryScreen
import com.example.eaa.ui.screens.SettingsScreen
import com.example.eaa.ui.theme.ElevenAudioTheme
import com.example.eaa.util.KeychainHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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

        // Запрос WRITE_EXTERNAL_STORAGE на старых Android (для экспорта в Music/).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 1001)
            }
        }
        // Запрос POST_NOTIFICATIONS на Android 13+ для уведомления фонового плеера.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 1002)
            }
        }
        // Поднимаем foreground-сервис сразу, чтобы уведомление появилось при запуске.
        try {
            val svc = android.content.Intent(this, com.example.eaa.audio.PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start PlaybackService: ${t.message}")
        }

        setContent {
            ElevenAudioTheme {
                var screen by remember { mutableStateOf(Screen.GENERATOR) }
                // Единое состояние ключа для всех экранов. Инициализируется из Keystore.
                var apiKey by remember { mutableStateOf(KeychainHelper.get(this).orEmpty()) }

                when (screen) {
                    Screen.GENERATOR -> GeneratorScreen(
                        apiKey = apiKey,
                        apiService = apiService,
                        onOpenLibrary = { screen = Screen.LIBRARY },
                        onOpenSettings = { screen = Screen.SETTINGS }
                    )
                    Screen.LIBRARY -> LibraryScreen(
                        onBack = { screen = Screen.GENERATOR }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        onBack = { screen = Screen.GENERATOR },
                        onApiKeyChanged = { apiKey = it }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Не освобождаем плеер здесь — пусть играет после выхода. Если хотите
        // остановить при уходе — раскомментируйте:
        // PlayerHolder.stop()
    }

    private enum class Screen { GENERATOR, LIBRARY, SETTINGS }

    companion object {
        private const val TAG = "ElevenAudioGen"
    }
}
