package com.example.eaa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.eaa.api.ElevenLabsService
import com.example.eaa.audio.PlayerHolder
import com.example.eaa.ui.screens.GeneratorScreen
import com.example.eaa.ui.screens.LibraryScreen
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

        setContent {
            var screen by remember { mutableStateOf(Screen.GENERATOR) }
            when (screen) {
                Screen.GENERATOR -> GeneratorScreen(
                    apiService = apiService,
                    onOpenLibrary = { screen = Screen.LIBRARY }
                )
                Screen.LIBRARY -> LibraryScreen(onBack = { screen = Screen.GENERATOR })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Не освобождаем плеер здесь — пусть играет после выхода. Если хотите
        // остановить при уходе — раскомментируйте:
        // PlayerHolder.stop()
    }

    private enum class Screen { GENERATOR, LIBRARY }

    companion object {
        private const val TAG = "ElevenAudioGen"
    }
}
