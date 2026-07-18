package com.example.eaa.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for ElevenLabs TTS API.
 */
interface ElevenLabsService {
    @GET("voices")
    suspend fun fetchVoices(@Header("xi-api-key") apiKey: String): VoiceResponse

    /**
     * Generate speech for the given voice.
     * @param outputFormat e.g. "mp3_44100_128" — передаётся в query, не в body
     */
    @POST("text-to-speech/{voiceId}")
    suspend fun synthesize(
        @Path("voiceId") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Query("output_format") outputFormat: String,
        @Body request: SynthesizeRequest
    ): ResponseBody
}

@JsonClass(generateAdapter = true)
data class VoiceResponse(
    @Json(name = "voices") val voices: List<Voice>
)

@JsonClass(generateAdapter = true)
data class Voice(
    @Json(name = "voice_id") val id: String,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class SynthesizeRequest(
    @Json(name = "text") val text: String,
    @Json(name = "model_id") val modelId: String = "eleven_multilingual_v2",
    @Json(name = "voice_settings") val voiceSettings: VoiceSettings
)

@JsonClass(generateAdapter = true)
data class VoiceSettings(
    @Json(name = "stability") val stability: Double,
    @Json(name = "similarity_boost") val similarityBoost: Double,
    @Json(name = "style") val style: Double = 0.0,
    @Json(name = "use_speaker_boost") val useSpeakerBoost: Boolean = true,
    @Json(name = "speed") val speed: Double = 1.0,
    @Json(name = "pitch") val pitch: Double = 0.0
)
