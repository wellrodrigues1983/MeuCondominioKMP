package br.tec.wrcoder.meucondominio.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import org.koin.core.context.GlobalContext

class AndroidFileOpener(private val context: Context) : FileOpener {
    override suspend fun openPdf(bytes: ByteArray, fileName: String): Boolean {
        val dir = File(context.cacheDir, "shared_files").apply { mkdirs() }
        val safeName = fileName.ifBlank { "documento.pdf" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(view, "Abrir PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }
}

actual fun createFileOpener(): FileOpener {
    val ctx = GlobalContext.get().get<Context>()
    return AndroidFileOpener(ctx.applicationContext)
}
