package com.voxmind.ai.ai

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AiClient(context: Context) {
    private val prefs = context.getSharedPreferences("voxmind_settings", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build()
    fun apiKey() = prefs.getString("api_key", "") ?: ""
    fun saveApiKey(key: String) { prefs.edit().putString("api_key", key.trim()).apply() }
    fun baseUrl() = ((prefs.getString("base_url", "https://api.openai.com/v1/") ?: "https://api.openai.com/v1/").trim().trimEnd('/') + "/")
    fun saveBaseUrl(url: String) { prefs.edit().putString("base_url", url.trim()).apply() }

    private fun builder(url: String): Request.Builder {
        val key = apiKey()
        require(key.isNotBlank()) { "Masukkan API key di Pengaturan terlebih dahulu." }
        return Request.Builder().url(url).header("Authorization", "Bearer $key")
    }

    fun transcribe(audio: File): String {
        require(audio.exists() && audio.length() > 1024) { "File audio tidak ditemukan atau terlalu kecil." }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "gpt-4o-mini-transcribe")
            .addFormDataPart("language", "id")
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
            .build()
        http.newCall(builder(baseUrl() + "audio/transcriptions").post(body).build()).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Transkripsi gagal HTTP ${r.code}: ${raw.take(400)}")
            return JSONObject(raw).optString("text").also { require(it.isNotBlank()) { "API tidak mengembalikan teks transkripsi." } }
        }
    }

    fun summarize(transcript: String) = askText(
        "Ringkas transkrip ini dalam Bahasa Indonesia dengan bagian RINGKASAN, POIN PENTING, TUGAS/AKSI, TANGGAL/ANGKA/NAMA. Jangan mengarang; hal yang tidak jelas harus ditandai perlu dikonfirmasi.\n\nTRANSKRIP:\n${transcript.take(60000)}"
    )

    fun ask(transcript: String, question: String) = askText(
        "Jawab dalam Bahasa Indonesia hanya berdasarkan transkrip berikut. Jika jawabannya tidak ada, katakan informasi tidak ditemukan.\nTRANSKRIP:\n${transcript.take(50000)}\n\nPERTANYAAN:\n${question.take(3000)}"
    )

    private fun askText(input: String): String {
        val body = JSONObject().put("model", "gpt-4.1-mini").put("input", input).put("max_output_tokens", 1200)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        http.newCall(builder(baseUrl() + "responses").post(body).build()).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("AI gagal HTTP ${r.code}: ${raw.take(400)}")
            val output = JSONObject(raw).optJSONArray("output") ?: JSONArray()
            val texts = mutableListOf<String>()
            for (i in 0 until output.length()) {
                val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    if (part.optString("type") == "output_text") texts += part.optString("text")
                }
            }
            return texts.joinToString("\n").trim().also { require(it.isNotBlank()) { "API tidak mengembalikan teks." } }
        }
    }
}
