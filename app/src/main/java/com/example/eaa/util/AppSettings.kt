package com.example.eaa.util

import android.content.Context

/**
 * Простые пользовательские настройки (модель TTS, тема и т.п.),
 * которые не нужно прятать в Keystore. Хранятся в SharedPreferences
 * "eaa_app_settings".
 */
object AppSettings {
    private const val PREF_NAME = "eaa_app_settings"
    private const val KEY_MODEL = "model_id"
    private const val KEY_THEME = "theme_mode"

    enum class ThemeMode(val key: String) {
        System("system"),
        Light("light"),
        Dark("dark");
        companion object {
            fun fromKey(k: String?): ThemeMode = entries.firstOrNull { it.key == k } ?: System
        }
    }

    /** Доступные модели. */
    data class ModelOption(val id: String, val label: String, val hint: String)

    val MODELS: List<ModelOption> = listOf(
        ModelOption(
            id = "eleven_multilingual_v2",
            label = "Multilingual v2",
            hint = "Лучшее качество, поддержка RU/EN и др. 1 кр. ≈ 1 символ."
        ),
        ModelOption(
            id = "eleven_turbo_v2_5",
            label = "Turbo v2.5",
            hint = "Быстрее и дешевле, EN + EU. ~0.5 кр. за символ."
        ),
        ModelOption(
            id = "eleven_flash_v2_5",
            label = "Flash v2.5",
            hint = "Самая быстрая и дешёвая, EN. ~0.3 кр. за символ."
        )
    )

    fun getModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODEL, null)
        return saved?.takeIf { it in MODELS.map { m -> m.id } } ?: MODELS.first().id
    }

    fun setModel(context: Context, modelId: String) {
        if (MODELS.none { it.id == modelId }) return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL, modelId).apply()
    }

    fun getTheme(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return ThemeMode.fromKey(prefs.getString(KEY_THEME, ThemeMode.System.key))
    }

    fun setTheme(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.key).apply()
    }
}
