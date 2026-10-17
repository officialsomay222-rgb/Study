package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.CookieManager
import com.example.ui.pdf.DocxToPdfConverter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Universal helper for opening external files (PDFs, Word .docx/.doc, text documents)
 * from any Android application (WhatsApp, Telegram, Gmail, Drive, Chrome, Samsung My Files, Google Files, etc.)
 * as well as handling downloads and web URLs.
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
     * Copies content from any URI into a private destination file using multiple fallback mechanisms.
     */
    fun copyUriToLocal(context: Context, uri: Uri, destFile: File, expectedName: String? = null): Boolean {
        if (destFile.exists()) {
            destFile.delete()
        }

        // Strategy 0: Check if file already exists in public Downloads directory
        try {
            val candidateNames = mutableListOf<String>()
            expectedName?.let { candidateNames.add(it) }
            val seg = uri.lastPathSegment
            if (!seg.isNullOrBlank()) {
                candidateNames.add(seg)
                try { candidateNames.add(URLDecoder.decode(seg, "UTF-8")) } catch (e: Exception) {}
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                for (name in candidateNames) {
                    val clean = name.substringAfterLast("/").substringAfterLast("\\")
                    val pubFile = File(downloadsDir, clean)
                    if (pubFile.exists() && pubFile.length() > 512 && pubFile.canRead()) {
                        pubFile.copyTo(destFile, overwrite = true)
                        if (destFile.exists() && destFile.length() > 0) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Strategy 1: ContentResolver openInputStream
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
                val srcFile = File(path)
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

        // Strategy 5: HTTP/HTTPS download with Session Cookies and User-Agent
        if (uri.scheme == "http" || uri.scheme == "https") {
            try {
                val urlStr = uri.toString()
                var currentUrl = urlStr
                var connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 25000
                connection.instanceFollowRedirects = true

                val cookie = CookieManager.getInstance().getCookie(currentUrl)
                if (!cookie.isNullOrEmpty()) {
                    connection.setRequestProperty("Cookie", cookie)
                }
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/msword,*/*")

                var redirectCount = 0
                while (redirectCount < 5) {
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
                        connection.readTimeout = 25000
                        connection.instanceFollowRedirects = true
                        val newCookie = CookieManager.getInstance().getCookie(currentUrl)
                        if (!newCookie.isNullOrEmpty()) {
                            connection.setRequestProperty("Cookie", newCookie)
                        }
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36")
                        connection.setRequestProperty("Accept", "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/msword,*/*")
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
     * Inspects a local file and sanitizes it if it contains leading bytes before '%PDF-'.
     */
    fun sanitizePdfFile(file: File): File {
        if (!file.exists() || file.length() < 5) return file

        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(minOf(file.length().toInt(), 4096))
                val bytesRead = fis.read(buffer)
                if (bytesRead >= 5) {
                    val pdfHeader = "%PDF-".toByteArray(Charsets.US_ASCII)
                    var offset = -1
                    for (i in 0..(bytesRead - 5)) {
                        if (buffer[i] == pdfHeader[0] &&
                            buffer[i + 1] == pdfHeader[1] &&
                            buffer[i + 2] == pdfHeader[2] &&
                            buffer[i + 3] == pdfHeader[3] &&
                            buffer[i + 4] == pdfHeader[4]
                        ) {
                            offset = i
                            break
                        }
                    }

                    if (offset > 0) {
                        // Strip leading bytes before %PDF-
                        val cleanFile = File(file.parentFile, "sanitized_${file.name}")
                        FileOutputStream(cleanFile).use { fos ->
                            fos.write(buffer, offset, bytesRead - offset)
                            val copyBuf = ByteArray(8192)
                            var count = fis.read(copyBuf)
                            while (count != -1) {
                                fos.write(copyBuf, 0, count)
                                count = fis.read(copyBuf)
                            }
                        }
                        if (cleanFile.exists() && cleanFile.length() > 0) {
                            return cleanFile
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return file
    }

    /**
     * Complete pipeline: imports any external Uri (PDF or Word), converts Word files to PDF,
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
            if (convertedPdf.exists() && convertedPdf.length() > 0) {
                val cleanTitle = displayName
                    .removeSuffix(".docx").removeSuffix(".DOCX")
                    .removeSuffix(".doc").removeSuffix(".DOC") + ".pdf"
                return Pair(convertedPdf, cleanTitle)
            }
        }

        // Sanitize PDF (strips leading BOM/junk headers if present)
        val readyPdf = sanitizePdfFile(localDest)
        val finalTitle = if (displayName.endsWith(".pdf", ignoreCase = true)) displayName else "$displayName.pdf"
        return Pair(readyPdf, finalTitle)
    }
}
