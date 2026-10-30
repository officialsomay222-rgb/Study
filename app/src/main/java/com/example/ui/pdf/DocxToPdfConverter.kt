package com.example.ui.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.math.max

/**
 * High-performance, offline Word (DOCX/DOC) and Text to PDF converter.
 * Converts external office files into crisp, multi-page PDFs using Android's native PdfDocument engine.
 */
object DocxToPdfConverter {

    data class ParagraphItem(
        val text: String,
        val isHeading: Boolean = false,
        val isBullet: Boolean = false,
        val headingLevel: Int = 1
    )

    /**
     * Checks if the given file or display name corresponds to a Word document or text file.
     */
    fun isWordOrOfficeDocument(fileName: String?, mimeType: String?): Boolean {
        val name = fileName?.lowercase() ?: ""
        val mime = mimeType?.lowercase() ?: ""
        return name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".dotx") || name.endsWith(".dot") ||
                name.endsWith(".txt") || name.endsWith(".rtf") ||
                mime.contains("wordprocessingml") || mime.contains("msword") ||
                mime.contains("vnd.ms-word") || mime.contains("text/plain")
    }

    /**
     * Converts a DOCX, DOC, or TXT file into a formatted multi-page PDF.
     * Returns the generated PDF file.
     */
    fun convertToPdf(context: Context, sourceFile: File, outputTitle: String): File {
        val cacheFolder = File(context.cacheDir, "study_pdfs").apply { mkdirs() }
        val safeName = outputTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .removeSuffix(".docx").removeSuffix(".DOCX")
            .removeSuffix(".doc").removeSuffix(".DOC")
            .removeSuffix(".txt").removeSuffix(".TXT")
        val outputPdfFile = File(cacheFolder, "converted_${System.currentTimeMillis()}_$safeName.pdf")

        val paragraphs = try {
            if (isDocxArchive(sourceFile)) {
                parseDocx(sourceFile)
            } else if (sourceFile.name.endsWith(".doc", ignoreCase = true)) {
                parseDocOrBinary(sourceFile)
            } else {
                parsePlainText(sourceFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: extract plain text
            parsePlainText(sourceFile)
        }

        renderParagraphsToPdf(paragraphs, outputPdfFile, outputTitle)
        return outputPdfFile
    }

    private fun isDocxArchive(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                // ZIP magic bytes: 'P' 'K' 0x03 0x04
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parses standard Office Open XML (.docx) by reading word/document.xml from the ZIP container.
     */
    private fun parseDocx(file: File): List<ParagraphItem> {
        val items = mutableListOf<ParagraphItem>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    items.addAll(parseDocumentXml(zis))
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (items.isEmpty()) {
            items.add(ParagraphItem("No text content found in document.", isHeading = false))
        }
        return items
    }

    private fun parseDocumentXml(inputStream: InputStream): List<ParagraphItem> {
        val items = mutableListOf<ParagraphItem>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentParagraph = StringBuilder()
        var inParagraph = false
        var isHeading = false
        var headingLevel = 1
        var isBullet = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "p" -> {
                            inParagraph = true
                            currentParagraph = StringBuilder()
                            isHeading = false
                            headingLevel = 1
                            isBullet = false
                        }
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.contains("Heading", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                isHeading = true
                                val levelChar = styleVal.lastOrNull { it.isDigit() }
                                headingLevel = levelChar?.digitToIntOrNull() ?: 1
                            }
                        }
                        "numPr" -> {
                            isBullet = true
                        }
                        "t" -> {
                            if (inParagraph) {
                                val text = parser.nextText()
                                currentParagraph.append(text)
                            }
                        }
                        "tab" -> {
                            if (inParagraph) currentParagraph.append("    ")
                        }
                        "br" -> {
                            if (inParagraph) currentParagraph.append("\n")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "p" && inParagraph) {
                        val text = currentParagraph.toString().trim()
                        if (text.isNotEmpty()) {
                            items.add(
                                ParagraphItem(
                                    text = text,
                                    isHeading = isHeading,
                                    isBullet = isBullet,
                                    headingLevel = headingLevel
                                )
                            )
                        }
                        inParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }

    /**
     * Extracts text from older binary .doc format or fallback files.
     */
    private fun parseDocOrBinary(file: File): List<ParagraphItem> {
        val items = mutableListOf<ParagraphItem>()
        try {
            FileInputStream(file).use { fis ->
                val bytes = fis.readBytes()
                val textBuilder = StringBuilder()
                var consecutivePrintable = 0

                for (b in bytes) {
                    val c = b.toInt().toChar()
                    if (c in ' '..'~' || c == '\n' || c == '\r' || c == '\t') {
                        textBuilder.append(c)
                        consecutivePrintable++
                    } else {
                        if (consecutivePrintable >= 4) {
                            textBuilder.append(" ")
                        }
                        consecutivePrintable = 0
                    }
                }

                val lines = textBuilder.toString().split("\n", "\r")
                for (line in lines) {
                    val clean = line.replace("\\s+".toRegex(), " ").trim()
                    if (clean.length > 3) {
                        val isH = clean.length < 50 && (clean.all { it.isUpperCase() || it.isWhitespace() } || clean.endsWith(":"))
                        items.add(ParagraphItem(clean, isHeading = isH))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items.ifEmpty { parsePlainText(file) }
    }

    private fun parsePlainText(file: File): List<ParagraphItem> {
        val items = mutableListOf<ParagraphItem>()
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
                    val isHeading = trimmed.length < 45 && (trimmed.endsWith(":") || trimmed.startsWith("#"))
                    val cleanText = if (trimmed.startsWith("#")) trimmed.trimStart('#').trim() else trimmed
                    items.add(ParagraphItem(cleanText, isHeading = isHeading, isBullet = isBullet))
                }
            }
        }
        return items
    }

    /**
     * Renders parsed paragraphs onto standard A4 PDF pages with elegant typography.
     */
    private fun renderParagraphsToPdf(
        paragraphs: List<ParagraphItem>,
        outputFile: File,
        documentTitle: String
    ) {
        val pdfDocument = PdfDocument()

        // Standard A4 dimensions at 72 DPI (595 x 842 points)
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 48
        val marginTop = 54
        val marginBottom = 50
        val contentWidth = pageWidth - (marginX * 2)
        val maxContentHeight = pageHeight - marginTop - marginBottom

        // Paints for typography
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 18f
            isFakeBoldText = true
            color = Color.rgb(20, 35, 75)
        }

        val heading1Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 15f
            isFakeBoldText = true
            color = Color.rgb(30, 50, 95)
        }

        val heading2Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 13.5f
            isFakeBoldText = true
            color = Color.rgb(45, 65, 110)
        }

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.rgb(33, 37, 41)
        }

        val bulletPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.rgb(33, 37, 41)
        }

        val headerFooterPaint = Paint().apply {
            isAntiAlias = true
            textSize = 8.5f
            color = Color.rgb(140, 145, 155)
        }

        val headerLinePaint = Paint().apply {
            color = Color.rgb(220, 225, 235)
            strokeWidth = 0.8f
        }

        var currentPageIndex = 0
        var currentY = marginTop
        var currentPage: PdfDocument.Page? = null
        var currentCanvas: Canvas? = null

        fun startNewPage(): Canvas {
            currentPage?.let {
                drawPageFooter(currentCanvas!!, currentPageIndex, pageWidth, pageHeight)
                pdfDocument.finishPage(it)
            }
            currentPageIndex++
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageIndex).create()
            val newPage = pdfDocument.startPage(pageInfo)
            currentPage = newPage
            val canvas = newPage.canvas

            // Fill page background white
            canvas.drawColor(Color.WHITE)

            // Draw header line and document title
            val cleanTitle = documentTitle.take(50)
            canvas.drawText(cleanTitle, marginX.toFloat(), 34f, headerFooterPaint)
            canvas.drawLine(marginX.toFloat(), 40f, (pageWidth - marginX).toFloat(), 40f, headerLinePaint)

            currentY = marginTop
            currentCanvas = canvas
            return canvas
        }

        try {
            var canvas = startNewPage()

            for (item in paragraphs) {
                val paint = when {
                    item.isHeading && item.headingLevel <= 1 -> heading1Paint
                    item.isHeading -> heading2Paint
                    item.isBullet -> bulletPaint
                    else -> bodyPaint
                }

                val prefix = if (item.isBullet) "  •  " else ""
                val fullText = prefix + item.text

                val textWidth = if (item.isBullet) contentWidth - 14 else contentWidth

                val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(fullText, 0, fullText.length, paint, textWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2.5f, 1.12f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(
                        fullText,
                        paint,
                        textWidth,
                        Layout.Alignment.ALIGN_NORMAL,
                        1.12f,
                        2.5f,
                        false
                    )
                }

                val layoutHeight = staticLayout.height
                val spacingBefore = if (item.isHeading) 14 else 6
                val spacingAfter = if (item.isHeading) 8 else 6

                // Check if paragraph fits on current page
                if (currentY + spacingBefore + layoutHeight > pageHeight - marginBottom) {
                    canvas = startNewPage()
                } else {
                    currentY += spacingBefore
                }

                canvas.save()
                val drawX = if (item.isBullet) marginX + 14f else marginX.toFloat()
                canvas.translate(drawX, currentY.toFloat())
                staticLayout.draw(canvas)
                canvas.restore()

                currentY += layoutHeight + spacingAfter
            }

            currentPage?.let {
                drawPageFooter(currentCanvas!!, currentPageIndex, pageWidth, pageHeight)
                pdfDocument.finishPage(it)
                currentPage = null
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            try {
                currentPage?.let { pdfDocument.finishPage(it) }
            } catch (_: Throwable) {}
            try {
                pdfDocument.close()
            } catch (_: Throwable) {}
        }
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, pageWidth: Int, pageHeight: Int) {
        val footerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 8.5f
            color = Color.rgb(140, 145, 155)
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.rgb(220, 225, 235)
            strokeWidth = 0.8f
        }
        val footerY = pageHeight - 28f
        canvas.drawLine(48f, footerY - 10f, (pageWidth - 48).toFloat(), footerY - 10f, linePaint)
        canvas.drawText("Page $pageNumber", (pageWidth / 2f), footerY + 4f, footerPaint)
    }
}
