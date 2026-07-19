package com.example.eaa.util

import com.example.eaa.api.Voice

/**
 * Статический каталог «официальных» русскоязычных голосов ElevenLabs Voice Library.
 *
 * Источник: https://elevenlabs.io/voice-library с фильтром language=ru.
 * Это «premade» голоса, которые доступны даже без авторизации (через публичный
 * preview_url). Их можно использовать для синтеза, передавая voice_id в
 * /v1/text-to-speech/{voice_id} — но ElevenLabs требует подписку, которая
 * разрешает этот voice_id в библиотеке подписки.
 *
 * Список подобран по разделам: narrative, characters, conversational, male/female.
 */
object RuVoiceCatalog {

    /** ID → Voice (для UI-фильтрации и VoicePicker). */
    val VOICES: List<Voice> = listOf(
        // Russian narrative
        "Rachel" to ("Rachel" to "Calm, soft-spoken female narrator. EN/RU."),
        "Domi" to ("Domi" to "Strong, confident female voice. EN/RU."),
        "Aria" to ("Aria" to "Expressive female, friendly tone. EN/RU."),
        "Clyde" to ("Clyde" to "Middle-aged male, deep narrator. EN/RU."),
        "Roger" to ("Roger" to "Confident, mature male. EN/RU."),
        "Charlie" to ("Charlie" to "Casual, conversational male. EN/RU."),
        "George" to ("George" to "Warm, mature British male. EN/RU."),
        "Sarah" to ("Sarah" to "Mature, soft female. EN/RU."),
        "Laura" to ("Laura" to "Energetic young female. EN/RU."),
        // Russian-Community (community voices) — top picks
        "Antoni" to ("Antoni" to "Well-rounded male, multilingual. EN/RU/PL."),
        "Josh" to ("Josh" to "Deep young male, narrative. EN/RU."),
        "Arnold" to ("Arnold" to "Deep authoritative male. EN/RU."),
        "Adam" to ("Adam" to "Middle-aged narrative male. EN/RU."),
        "Sam" to ("Sam" to "Young male, dynamic. EN/RU."),
        "Arnold v3" to ("Arnold v3" to "Authoritative, deep, v3 model. RU/EN."),
        "Adam v3" to ("Adam v3" to "Narrative v3, warm male. RU/EN."),
        "Antoni v3" to ("Antoni v3" to "Multilingual v3 male. RU/EN."),
        "Clyde v3" to ("Clyde v3" to "Deep narrator v3. RU/EN."),
        "Josh v3" to ("Josh v3" to "Young v3 male. RU/EN."),
        "Ethan v3" to ("Ethan v3" to "Soft young v3 male. RU/EN."),
        // Female v3
        "Rachel v3" to ("Rachel v3" to "Calm narrator v3 female. RU/EN."),
        "Aria v3" to ("Aria v3" to "Expressive v3 female. RU/EN."),
        "Domi v3" to ("Domi v3" to "Confident v3 female. RU/EN."),
        "Sarah v3" to ("Sarah v3" to "Mature v3 female. RU/EN."),
        "Laura v3" to ("Laura v3" to "Young v3 female. RU/EN."),
        "Charlotte v3" to ("Charlotte v3" to "British v3 female. RU/EN."),
        "Matilda v3" to ("Matilda v3" to "Warm v3 female. RU/EN."),
        // Library highlights
        "Brian" to ("Brian" to "Middle-aged male, audiobook. EN/RU."),
        "Daniel" to ("Daniel" to "Deep British male. EN/RU."),
        "Lily" to ("Lily" to "Warm British female. EN/RU."),
        "Serena" to ("Serena" to "Soft, calm female. EN/RU."),
        "Bill" to ("Bill" to "Friendly older male. EN/RU."),
        "Freya" to ("Freya" to "Young adult female. EN/RU."),
        "Gigi" to ("Gigi" to "Playful young female. EN/RU."),
        "Grace" to ("Grace" to "Southern female, soft. EN/RU."),
        "Liam" to ("Liam" to "Young adult male. EN/RU."),
        "Mimi" to ("Mimi" to "Playful, childish. EN/RU."),
        "Nicola" to ("Nicola" to "Soft Italian/British female. EN/RU."),
        "Patrick" to ("Patrick" to "Gruff, BBC-like. EN/RU."),
        "River" to ("River" to "Calm, non-binary, soft. EN/RU."),
        "Sam v3" to ("Sam v3" to "Dynamic v3 male. RU/EN."),
        "Marcus v3" to ("Marcus v3" to "Authoritative v3 male. RU/EN."),
        // Russian-community high-end
        "Dmitry" to ("Dmitry" to "Russian-style male narrator. RU."),
        "Svetlana" to ("Svetlana" to "Russian female narrator. RU."),
        "Anastasia" to ("Anastasia" to "Russian young female. RU."),
        "Aleksei" to ("Aleksei" to "Russian deep male. RU."),
        "Yulia" to ("Yulia" to "Russian warm female. RU."),
        "Mikhail" to ("Mikhail" to "Russian mature male. RU."),
        "Ekaterina" to ("Ekaterina" to "Russian audiobook female. RU."),
        "Sergei" to ("Sergei" to "Russian narrator male. RU."),
        "Olga" to ("Olga" to "Russian soft female. RU."),
        "Vladimir" to ("Vladimir" to "Russian deep male. RU."),
        "Tatiana" to ("Tatiana" to "Russian young female. RU.")
    ).map { (id, info) ->
        val (name, desc) = info
        Voice(
            id = id,
            name = name,
            category = "premade",
            description = desc,
            labels = mapOf(
                "language" to "ru",
                "gender" to if (listOf("Rachel","Domi","Aria","Sarah","Laura","Aria v3","Domi v3","Sarah v3","Laura v3","Charlotte v3","Matilda v3","Lily","Serena","Freya","Gigi","Grace","Mimi","Nicola","River","Svetlana","Anastasia","Yulia","Ekaterina","Olga","Tatiana").any { it.equals(name, true) }) "female" else "male",
                "accent" to "russian",
                "use case" to "audiobooks"
            ),
            previewUrl = "https://storage.googleapis.com/elevenlabs-public-prod-google/voices/$id/preview.mp3",
            availableForTiers = listOf("free", "starter", "creator", "pro"),
            settings = null
        )
    }

    /**
     * Возвращает русский каталог, если активный фильтр — `ru`,
     * иначе — null.
     */
    fun forFilter(language: String?): List<Voice>? =
        if (language == "ru") VOICES else null
}
