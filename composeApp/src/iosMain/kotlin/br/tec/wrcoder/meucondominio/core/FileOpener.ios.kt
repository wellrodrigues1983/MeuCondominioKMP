package br.tec.wrcoder.meucondominio.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
class IosFileOpener : FileOpener {
    override suspend fun openPdf(bytes: ByteArray, fileName: String): Boolean {
        val safeName = fileName.ifBlank { "documento.pdf" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val path = NSTemporaryDirectory() + safeName
        val url = NSURL.fileURLWithPath(path)
        val data = bytes.usePinned {
            NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong())
        }
        data.writeToURL(url, atomically = true)
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
        val activity = UIActivityViewController(listOf(url), null)
        root.presentViewController(activity, true, null)
        return true
    }
}

actual fun createFileOpener(): FileOpener = IosFileOpener()
