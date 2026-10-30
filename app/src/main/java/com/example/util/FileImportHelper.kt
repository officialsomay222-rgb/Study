package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.CookieManager
import com.example.ui.pdf.DocxToPdfConverter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Universal helper for opening external and internal files (PDFs, Word .docx/.doc, images, text documents,
 * cloud links, data URIs, and web downloads) with automatic repair and verification.
 */
object FileImportHelper {

    /**
     * Extracts the target file Uri from any type of Intent (VIEW, SEND, SEND_MULTIPLE, EDIT, etc.).
     */
    fun extractUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null

        // 1. Standard intent data URI
        intent.data?.let { return it }

        // 2. ClipData URI (used by modern file managers, Gmail, WhatsApp)
        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                clipData.getItemAt(0)?.uri?.let { return it }
            }
        }

        // 3. EXTRA_STREAM parcelable (used by share sheets and ACTION_SEND)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
            } else {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { return it }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. ACTION_SEND_MULTIPLE list
        try {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()?.let { return it }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Data string fallback
        intent.dataString?.let { str ->
            try {
                if (str.isNotBlank()) return Uri.parse(str)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Resolves a human-readable display name for the document from ContentResolver or URI path.
     */
    fun resolveDisplayName(context: Context, uri: Uri): String {
        var name: String? = null

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIdx != -1) {
                            name = cursor.getString(colIdx)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (name.isNullOrBlank()) {
            name = try {
                val lastSegment = uri.lastPathSegment
                if (!lastSegment.isNullOrBlank()) {
                    URLDecoder.decode(lastSegment, "UTF-8")
                } else null
            } catch (e: Exception) {
                uri.lastPathSegment
            }
        }

        if (name.isNullOrBlank()) {
            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            name = if (mimeType?.contains("word") == true || mimeType?.contains("officedocument") == true) {
                "Document.docx"
            } else {
                "Document.pdf"
            }
        }

        return name ?: "Document.pdf"
    }

    /**
     * Checks if the document is a Word document (.docx, .doc), text file, or Office document that needs conversion.
     */
    fun isWordOrOfficeDocument(fileName: String?, mimeType: String?, uri: Uri? = null): Boolean {
        val lowerName = fileName?.lowercase() ?: ""
        val lowerMime = mimeType?.lowercase() ?: ""
        val uriStr = uri?.toString()?.lowercase() ?: ""

        if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc") ||
            lowerName.endsWith(".dotx") || lowerName.endsWith(".dot") ||
            lowerName.endsWith(".txt") || lowerName.endsWith(".rtf")
        ) {
            return true
        }

        if (lowerMime.contains("wordprocessingml") || lowerMime.contains("msword") ||
            lowerMime.contains("vnd.ms-word") || lowerMime.contains("text/plain")
        ) {
            return true
        }

        if (uriStr.contains(".docx") || uriStr.contains(".doc")) {
            return true
        }

        return false
    }

    /**
     * Checks if a file is a DOCX ZIP archive by inspecting the magic bytes.
     */
    fun isDocxZip(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether a local file is a COMPLETE PDF (has both '%PDF-' header and '%%EOF' trailer)
     * so we never open a partially-downloaded file while DownloadManager is still writing it.
     */
    fun isCompletePdfFile(file: File): Boolean {
        if (!file.exists() || !file.canRead() || file.length() < 128L) return false
        return try {
            val headSize = minOf(file.length().toInt(), 4096)
            val headBuf = ByteArray(headSize)
            FileInputStream(file).use { fis ->
                fis.read(headBuf)
            }
            val headStr = String(headBuf, Charsets.ISO_8859_1)
            if (!headStr.contains("%PDF-")) return false

            val tailSize = minOf(file.length().toInt(), 4096)
            val tailBuf = ByteArray(tailSize)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(file.length() - tailSize)
                raf.readFully(tailBuf)
            }
            val tailStr = String(tailBuf, Charsets.ISO_8859_1)
            tailStr.contains("%%EOF")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalizes cloud viewer URLs (Google Drive, Google Docs, Dropbox, GitHub) into direct PDF download URLs.
     */
    fun normalizeDocumentUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()

        // Google Drive /file/d/{id}/view -> direct download
        val driveFileRegex = Regex("""https?://drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
        driveFileRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { fileId ->
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        // Google Drive /open?id={id}
        val driveOpenRegex = Regex("""https?://drive\.google\.com/open\?id=([a-zA-Z0-9_-]+)""")
        driveOpenRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { fileId ->
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        // Google Docs /document/d/{id}/edit -> export as PDF
        val docsRegex = Regex("""https?://docs\.google\.com/document/d/([a-zA-Z0-9_-]+)""")
        docsRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { docId ->
            return "https://docs.google.com/document/d/$docId/export?format=pdf"
        }

        // Dropbox ?dl=0 -> ?dl=1
        if (trimmed.contains("dropbox.com", ignoreCase = true) && trimmed.endsWith("?dl=0")) {
            return trimmed.removeSuffix("?dl=0") + "?dl=1"
        }

        // GitHub blob -> raw
        if (trimmed.contains("github.com/", ignoreCase = true) && trimmed.contains("/blob/")) {
            return trimmed
                .replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("/blob/", "/")
        }

        return trimmed
    }

    /**
     * Copies content from any URI into a private destination file using multiple fallback mechanisms.
     */
    fun copyUriToLocal(context: Context, uri: Uri, destFile: File, expectedName: String? = null): Boolean {
        if (destFile.exists()) {
            destFile.delete()
        }

        val uriString = uri.toString()

        // Strategy A: data: URI (e.g., data:application/pdf;base64,...)
        if (uriString.startsWith("data:", ignoreCase = true)) {
            try {
                val commaIndex = uriString.indexOf(',')
                if (commaIndex != -1) {
                    val meta = uriString.substring(0, commaIndex)
                    val payload = uriString.substring(commaIndex + 1)
                    val bytes = if (meta.contains(";base64", ignoreCase = true)) {
                        Base64.decode(payload, Base64.DEFAULT)
                    } else {
                        URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
                    }
                    if (bytes.isNotEmpty()) {
                        FileOutputStream(destFile).use { it.write(bytes) }
                        if (destFile.exists() && destFile.length() > 0) return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Strategy 0: Check if a COMPLETE file already exists in public Downloads directory
        try {
            val candidateNames = mutableListOf<String>()
            expectedName?.let { candidateNames.add(it) }
            val seg = uri.lastPathSegment
            if (!seg.isNullOrBlank()) {
                candidateNames.add(seg)
                try { candidateNames.add(URLDecoder.decode(seg, "UTF-8")) } catch (_: Exception) {}
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                for (name in candidateNames) {
                    val clean = name.substringAfterLast("/").substringAfterLast("\\")
                    val pubFile = File(downloadsDir, clean)
                    if (pubFile.exists() && pubFile.length() > 512 && pubFile.canRead()) {
                        // Only use if it is a complete PDF or a complete Office archive
                        if (isCompletePdfFile(pubFile) || isDocxZip(pubFile)) {
                            pubFile.copyTo(destFile, overwrite = true)
                            if (destFile.exists() && destFile.length() > 0) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Strategy 1: ContentResolver openInputStream
        if (uri.scheme == "content" || uri.scheme == "android.resource") {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Strategy 2: ParcelFileDescriptor
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Strategy 3: AssetFileDescriptor
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.createInputStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Strategy 4: File scheme or raw local path
        try {
            val path = if (uri.scheme == "file") {
                uri.path ?: uri.toString().removePrefix("file://")
            } else if (uri.scheme == null) {
                uri.path ?: uri.toString()
            } else {
                null
            }

            if (path != null) {
                val decodedPath = try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path }
                val srcFile = if (File(path).exists()) File(path) else File(decodedPath)
                if (srcFile.exists() && srcFile.canRead() && srcFile.length() > 0) {
                    srcFile.copyTo(destFile, overwrite = true)
                    if (destFile.exists() && destFile.length() > 0) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Strategy 5: HTTP/HTTPS download with Session Cookies, Normalized Cloud URLs, and User-Agent
        if (uri.scheme == "http" || uri.scheme == "https") {
            try {
                var currentUrl = normalizeDocumentUrl(uri.toString())
                var connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true

                val cookie = CookieManager.getInstance().getCookie(currentUrl)
                if (!cookie.isNullOrEmpty()) {
                    connection.setRequestProperty("Cookie", cookie)
                }
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/msword,application/octet-stream,*/*")

                var redirectCount = 0
                while (redirectCount < 8) {
                    connection.connect()
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val newLocation = connection.getHeaderField("Location") ?: break
                        currentUrl = if (newLocation.startsWith("http://") || newLocation.startsWith("https://")) {
                            newLocation
                        } else {
                            URL(URL(currentUrl), newLocation).toString()
                        }
                        connection.disconnect()
                        connection = URL(currentUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 15000
                        connection.readTimeout = 30000
                        connection.instanceFollowRedirects = true
                        val newCookie = CookieManager.getInstance().getCookie(currentUrl)
                        if (!newCookie.isNullOrEmpty()) {
                            connection.setRequestProperty("Cookie", newCookie)
                        }
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36")
                        connection.setRequestProperty("Accept", "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/msword,application/octet-stream,*/*")
                        redirectCount++
                    } else {
                        break
                    }
                }

                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                connection.disconnect()
                if (destFile.exists() && destFile.length() > 0) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Clean up failed 0-byte file
        if (destFile.exists() && destFile.length() == 0L) {
            destFile.delete()
        }

        return false
    }

    /**
     * Tests whether Android's native PdfRenderer can open the file and read at least 1 page.
     */
    fun canOpenWithPdfRenderer(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            count > 0
        } catch (e: Exception) {
            false
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Converts an image file (PNG/JPEG/WEBP) into a high-resolution single-page PDF.
     */
    private fun convertImageToPdf(context: Context, imageFile: File): File? {
        var bmp: android.graphics.Bitmap? = null
        var pdfDoc: PdfDocument? = null
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > 2048 || boundsOpts.outHeight / sampleSize > 2048) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            bmp = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOpts) ?: return null
            val cacheFolder = File(context.cacheDir, "study_pdfs").apply { mkdirs() }
            val outPdf = File(cacheFolder, "img_${System.currentTimeMillis()}.pdf")

            val pageWidth = 1080
            val aspect = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
            val pageHeight = (pageWidth * aspect).toInt().coerceIn(720, 2400)

            pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val destRect = RectF(24f, 24f, (pageWidth - 24).toFloat(), (pageHeight - 24).toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bmp, null, destRect, paint)

            pdfDoc.finishPage(page)
            FileOutputStream(outPdf).use { pdfDoc.writeTo(it) }
            if (outPdf.exists() && outPdf.length() > 0) outPdf else null
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        } finally {
            try { pdfDoc?.close() } catch (_: Throwable) {}
            try { bmp?.recycle() } catch (_: Throwable) {}
        }
    }

    /**
     * Inspects a local file, copies it into app cache if needed, strips leading junk bytes before '%PDF-',
     * repairs missing '%%EOF' trailers, or converts Office/Image/Text/HTML content into a valid PDF
     * so PdfRenderer never fails.
     */
    fun sanitizePdfFile(file: File, context: Context? = null, title: String = file.name): File {
        if (!file.exists() || file.length() == 0L) return file

        val workDir = context?.let { File(it.cacheDir, "study_pdfs").apply { mkdirs() } }
            ?: file.parentFile

        // Ensure we are working on a file in our writable cache directory (avoids Scoped Storage EACCES on Downloads)
        val workingFile = if (workDir != null && file.parentFile?.absolutePath != workDir.absolutePath) {
            val copyTarget = File(workDir, "work_${System.currentTimeMillis()}_${file.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}")
            try {
                file.copyTo(copyTarget, overwrite = true)
                if (copyTarget.exists() && copyTarget.length() > 0) copyTarget else file
            } catch (_: Exception) {
                file
            }
        } else {
            file
        }

        try {
            // 1. Check if it's a DOCX / ZIP archive disguised as a PDF
            if (isDocxZip(workingFile) && context != null) {
                val converted = DocxToPdfConverter.convertToPdf(context, workingFile, title)
                if (canOpenWithPdfRenderer(converted)) return converted
            }

            // 2. Search for '%PDF-' header within the first 32KB
            var pdfHeaderOffset = -1
            FileInputStream(workingFile).use { fis ->
                val scanSize = minOf(workingFile.length().toInt(), 32768)
                val buffer = ByteArray(scanSize)
                val bytesRead = fis.read(buffer)
                if (bytesRead >= 5) {
                    val pdfHeader = "%PDF-".toByteArray(Charsets.US_ASCII)
                    for (i in 0..(bytesRead - 5)) {
                        if (buffer[i] == pdfHeader[0] &&
                            buffer[i + 1] == pdfHeader[1] &&
                            buffer[i + 2] == pdfHeader[2] &&
                            buffer[i + 3] == pdfHeader[3] &&
                            buffer[i + 4] == pdfHeader[4]
                        ) {
                            pdfHeaderOffset = i
                            break
                        }
                    }
                }
            }

            var candidatePdf = workingFile
            if (pdfHeaderOffset > 0 && workDir != null) {
                // Strip leading bytes before %PDF-
                val cleanFile = File(workDir, "sanitized_${System.currentTimeMillis()}_${workingFile.name}")
                FileInputStream(workingFile).use { fis ->
                    var skipped = 0L
                    while (skipped < pdfHeaderOffset) {
                        val s = fis.skip(pdfHeaderOffset - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    FileOutputStream(cleanFile).use { fos ->
                        val copyBuf = ByteArray(16384)
                        var count = fis.read(copyBuf)
                        while (count != -1) {
                            fos.write(copyBuf, 0, count)
                            count = fis.read(copyBuf)
                        }
                    }
                }
                if (cleanFile.exists() && cleanFile.length() > 0) {
                    candidatePdf = cleanFile
                }
            }

            // 3. If it has a %PDF- header, test if PdfRenderer can open it directly or if it has a valid %%EOF trailer
            if (pdfHeaderOffset >= 0) {
                if (canOpenWithPdfRenderer(candidatePdf) || isCompletePdfFile(candidatePdf)) {
                    return candidatePdf
                }
                // Try repairing missing %%EOF trailer
                if (workDir != null) {
                    val repairedFile = File(workDir, "repaired_${System.currentTimeMillis()}_${candidatePdf.name}")
                    candidatePdf.copyTo(repairedFile, overwrite = true)
                    FileOutputStream(repairedFile, true).use { fos ->
                        fos.write("\n%%EOF\n".toByteArray(Charsets.US_ASCII))
                    }
                    if (canOpenWithPdfRenderer(repairedFile) || isCompletePdfFile(repairedFile)) {
                        return repairedFile
                    }
                }
                if (context == null) {
                    return candidatePdf
                }
            }

            // 4. If not a valid native PDF (or corrupted beyond trailer), check if it's an image or text/office doc
            if (context != null) {
                convertImageToPdf(context, workingFile)?.let { imgPdf ->
                    if (canOpenWithPdfRenderer(imgPdf) || isCompletePdfFile(imgPdf)) return imgPdf
                }

                val convertedDoc = DocxToPdfConverter.convertToPdf(context, workingFile, title)
                if (canOpenWithPdfRenderer(convertedDoc) || isCompletePdfFile(convertedDoc)) {
                    return convertedDoc
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return workingFile
    }

    /**
     * Complete pipeline: imports any external Uri (PDF, Word, Image, Text, Cloud URL), converts if needed,
     * sanitizes PDFs, and returns a verified local PDF File ready for immediate display.
     */
    fun importAndPrepareDocument(context: Context, uri: Uri, customTitle: String? = null): Pair<File, String>? {
        val displayName = customTitle ?: resolveDisplayName(context, uri)
        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        val cacheFolder = File(context.cacheDir, "study_pdfs").apply { mkdirs() }

        val safeName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val isWord = isWordOrOfficeDocument(displayName, mimeType, uri)

        val rawFileName = if (isWord) {
            if (safeName.endsWith(".docx", ignoreCase = true) || safeName.endsWith(".doc", ignoreCase = true)) {
                safeName
            } else {
                "$safeName.docx"
            }
        } else {
            if (safeName.endsWith(".pdf", ignoreCase = true)) safeName else "$safeName.pdf"
        }

        val localDest = File(cacheFolder, "imp_${System.currentTimeMillis()}_$rawFileName")

        val copied = copyUriToLocal(context, uri, localDest, displayName)
        if (!copied || !localDest.exists() || localDest.length() == 0L) {
            return null
        }

        // Check if the file is a Word document or contains DOCX ZIP archive
        if (isWord || isDocxZip(localDest)) {
            val convertedPdf = DocxToPdfConverter.convertToPdf(context, localDest, displayName)
            if (convertedPdf.exists() && convertedPdf.length() > 0 && canOpenWithPdfRenderer(convertedPdf)) {
                val cleanTitle = displayName
                    .removeSuffix(".docx").removeSuffix(".DOCX")
                    .removeSuffix(".doc").removeSuffix(".DOC") + ".pdf"
                return Pair(convertedPdf, cleanTitle)
            }
        }

        // Sanitize, verify, or convert to ensure PdfRenderer never fails
        val readyPdf = sanitizePdfFile(localDest, context, displayName)
        val finalTitle = if (displayName.endsWith(".pdf", ignoreCase = true)) displayName else "$displayName.pdf"
        return Pair(readyPdf, finalTitle)
    }
}
