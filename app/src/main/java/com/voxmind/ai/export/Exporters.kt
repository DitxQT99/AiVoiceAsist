package com.voxmind.ai.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.voxmind.ai.data.Recording
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object Exporters {
    private fun text(item: Recording) = buildString {
        appendLine(item.title)
        appendLine("Tanggal: " + SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(item.createdAt)))
        appendLine("\nRINGKASAN\n" + item.summary.ifBlank { "Belum ada ringkasan." })
        appendLine("\nTRANSKRIP\n" + item.transcript.ifBlank { "Belum ada transkrip." })
    }
    fun txt(context: Context, item: Recording): File = File(context.cacheDir, "voxmind_${item.id}.txt").apply { writeText(text(item), Charsets.UTF_8) }
    fun pdf(context: Context, item: Recording): File {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 11f }
        val lines = text(item).lines().flatMap { if (it.isEmpty()) listOf("") else it.chunked(86) }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 40f
        for (line in lines) {
            if (y > 800) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 40f
            }
            page.canvas.drawText(line, 40f, y, paint); y += 16f
        }
        doc.finishPage(page)
        val out = File(context.cacheDir, "voxmind_${item.id}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
        return out
    }
    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Ekspor VoxMind"))
    }
}
