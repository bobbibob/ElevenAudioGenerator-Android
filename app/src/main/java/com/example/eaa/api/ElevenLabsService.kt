package com.example.eaa.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for ElevenLabs TTS API.
 */
interface ElevenLabsService {
    @GET("voices")
    suspend fun fetchVoices(@Header("xi-api-key") apiKey: String): VoiceResponse

    @POST("text-to-speech/{voiceId}")
    suspend fun synthesize(
        @Path("voiceId") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Body request: SynthesizeRequest
    ): okhttp3.ResponseBody // raw audio bytes (MP3 or WAV)
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
    @Json(name = "model_id") val modelId: String = "eleven_monolingual_v1",
    @Json(name = "voice_settings") val voiceSettings: VoiceSettings,
    @Json(name = "output_format") val outputFormat: String = "mp3"
)

@JsonClass(generateAdapter = true)
data class VoiceSettings(
    @Json(name = "stability") val stability: Double,
    @Json(name = "similarity_boost") val similarityBoost: Double,
    @Json(name = "speed") val speed: Double,
    @Json(name = "pitch") val pitch: Double
)
