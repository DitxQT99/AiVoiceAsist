package com.voxmind.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.voxmind.ai.ai.AiClient
import com.voxmind.ai.data.*
import com.voxmind.ai.export.Exporters
import com.voxmind.ai.recording.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { VoxMindApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoxMindApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val ai = remember { AiClient(context) }
    val scope = rememberCoroutineScope()
    val recordings by db.recordingDao().observeAll().collectAsState(initial = emptyList())
    var recording by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var settings by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(ai.apiKey()) }
    var base by remember { mutableStateOf(ai.baseUrl().removeSuffix("/")) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf(false) }
    val selected = recordings.firstOrNull { it.id == selectedId }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startForegroundService(context, Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START))
            recording = true
        } else status = "Izin mikrofon diperlukan."
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(topBar = { TopAppBar(title = { Text("VoxMind AI") }, actions = { TextButton(onClick = { settings = true }) { Text("Pengaturan") } }) }) { padding ->
            if (selected == null) {
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    Text("Rekam suara. Simpan poin penting.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (recording) "● MEREKAM" else "Siap merekam", style = MaterialTheme.typography.titleLarge)
                            Text("Perekaman tetap berjalan dengan notifikasi selama sistem mengizinkan.")
                            Spacer(Modifier.height(8.dp))
                            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                                if (!recording) {
                                    val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
                                    permission.launch(p.toTypedArray())
                                } else {
                                    context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
                                    recording = false
                                    status = "Rekaman dihentikan dan disimpan. Jika daftar belum berubah, tunggu sebentar."
                                }
                            }) { Text(if (recording) "Hentikan rekaman" else "Mulai merekam") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Cari judul atau transkrip") }, singleLine = true)
                    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Riwayat", style = MaterialTheme.typography.titleLarge)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recordings.filter { search.isBlank() || it.title.contains(search, true) || it.transcript.contains(search, true) || it.summary.contains(search, true) }, key = { it.id }) { item ->
                            Card(Modifier.fillMaxWidth().clickable { selectedId = item.id }) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(item.createdAt)))
                                    Text(if (item.transcript.isBlank()) "Belum diproses AI" else if (item.summary.isBlank()) "Transkrip tersedia" else "Transkrip dan ringkasan tersedia")
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { selectedId = null; chat = false; answer = "" }) { Text("← Kembali") }
                    Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                    Text("File audio tersedia: ${File(selected.audioPath).exists()}")
                    Button(enabled = !busy && File(selected.audioPath).exists(), modifier = Modifier.fillMaxWidth(), onClick = {
                        val item = selected
                        scope.launch {
                            busy = true; status = ""
                            try {
                                val transcript = withContext(Dispatchers.IO) { ai.transcribe(File(item.audioPath)) }
                                val first = item.copy(transcript = transcript)
                                withContext(Dispatchers.IO) { db.recordingDao().update(first) }
                                val summary = withContext(Dispatchers.IO) { ai.summarize(transcript) }
                                withContext(Dispatchers.IO) { db.recordingDao().update(first.copy(summary = summary)) }
                                status = "Transkripsi dan ringkasan berhasil."
                            } catch (e: Exception) { status = e.message ?: "Pemrosesan API gagal." }
                            finally { busy = false }
                        }
                    }) { Text(if (busy) "Memproses..." else "Proses dengan AI (unggah audio)") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.secondary)
                    Text("Ringkasan & poin penting", style = MaterialTheme.typography.titleLarge)
                    Text(selected.summary.ifBlank { "Belum ada ringkasan." })
                    Text("Transkrip", style = MaterialTheme.typography.titleLarge)
                    Text(selected.transcript.ifBlank { "Belum ada transkrip." })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { try { Exporters.share(context, Exporters.txt(context, selected), "text/plain") } catch (e: Exception) { status = e.message ?: "Ekspor gagal" } }) { Text("Ekspor TXT") }
                        OutlinedButton(onClick = { try { Exporters.share(context, Exporters.pdf(context, selected), "application/pdf") } catch (e: Exception) { status = e.message ?: "Ekspor gagal" } }) { Text("Ekspor PDF") }
                    }
                    Button(enabled = selected.transcript.isNotBlank(), onClick = { chat = !chat }) { Text(if (chat) "Tutup chat" else "Tanya isi rekaman") }
                    if (chat) {
                        OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("Tulis pertanyaan") })
                        Button(enabled = !busy && question.isNotBlank(), onClick = {
                            val transcript = selected.transcript
                            scope.launch {
                                busy = true
                                try { answer = withContext(Dispatchers.IO) { ai.ask(transcript, question) } }
                                catch (e: Exception) { answer = "Gagal: ${e.message ?: "Kesalahan API"}" }
                                finally { busy = false }
                            }
                        }) { Text(if (busy) "Menunggu..." else "Kirim pertanyaan") }
                        if (answer.isNotBlank()) Text(answer)
                    }
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) { File(selected.audioPath).delete(); db.recordingDao().deleteById(selected.id) }
                        selectedId = null
                    }) { Text("Hapus rekaman dan catatan") }
                }
            }
        }
    }

    if (settings) AlertDialog(
        onDismissRequest = { settings = false },
        title = { Text("Pengaturan API") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Untuk uji pribadi saja: API key yang disimpan di aplikasi bisa diekstrak.")
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true)
            OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { ai.saveApiKey(key); ai.saveBaseUrl(base); settings = false; status = "Pengaturan disimpan." }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = { settings = false }) { Text("Batal") } }
    )
}
