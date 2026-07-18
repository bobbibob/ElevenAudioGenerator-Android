package com.example.eaa.util

/**
 * Разбивка длинного текста на чанки для ElevenLabs TTS.
 *
 * API ElevenLabs режет текст ~5 000 символов на запрос. Берём [maxChars] с запасом
 * (4 500) и режем по границам абзацев/предложений, чтобы склейка звучала естественно.
 */
object Chunker {

    /**
     * Разбить [text] на список чанков длиной ≤ [maxChars].
     * Пустые/короткие строки → один чанк. Очень длинные предложения режутся «в лоб».
     */
    fun split(text: String, maxChars: Int = 4500): List<String> {
        val src = text.trim()
        if (src.isEmpty()) return emptyList()
        if (src.length <= maxChars) return listOf(src)

        val out = mutableListOf<String>()
        // Сначала режем по абзацам (\n\n), потом по предложениям, потом по строкам,
        // потом — по символам (fallback).
        val paragraphs = src.split(Regex("\\n\\s*\\n"))
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out += buf.toString().trim()
                buf.setLength(0)
            }
        }

        for (para in paragraphs) {
            if (para.length > maxChars) {
                flush()
                // Слишком большой абзац — режем по предложениям
                val sentences = para.split(Regex("(?<=[.!?…])\\s+"))
                for (s in sentences) {
                    if (s.length > maxChars) {
                        // Даже одно предложение больше лимита — режем по строкам
                        for (line in s.split('\n')) {
                            if (line.length > maxChars) {
                                // В крайнем случае — фиксированные куски по maxChars
                                var i = 0
                                while (i < line.length) {
                                    val end = (i + maxChars).coerceAtMost(line.length)
                                    out += line.substring(i, end)
                                    i = end
                                }
                            } else {
                                if (buf.length + line.length + 1 > maxChars) flush()
                                if (buf.isNotEmpty()) buf.append('\n')
                                buf.append(line)
                            }
                        }
                    } else {
                        if (buf.length + s.length + 1 > maxChars) flush()
                        if (buf.isNotEmpty()) buf.append(' ')
                        buf.append(s)
                    }
                }
                flush()
            } else {
                if (buf.length + para.length + 2 > maxChars) flush()
                if (buf.isNotEmpty()) buf.append("\n\n")
                buf.append(para)
            }
        }
        flush()
        return out.filter { it.isNotBlank() }
    }
}
