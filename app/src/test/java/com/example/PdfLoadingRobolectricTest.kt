package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.FileImportHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfLoadingRobolectricTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    // Standard minimal valid PDF byte content
    private val validPdfBytes: ByteArray = (
        "%PDF-1.4\n" +
        "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
        "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n" +
        "3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<<>>>>endobj\n" +
        "xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \n" +
        "trailer<</Size 4/Root 1 0 R>>\nstartxref\n178\n%%EOF\n"
    ).toByteArray(Charsets.US_ASCII)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_pdfs").apply { mkdirs() }
    }

    private fun createSamplePdf(file: File): File {
        file.writeBytes(validPdfBytes)
        return file
    }

    @Test
    fun `test valid PDF file exists and has valid magic bytes`() {
        val samplePdf = File(testDir, "sample_test.pdf")
        createSamplePdf(samplePdf)

        assertTrue(samplePdf.exists())
        assertTrue(samplePdf.length() > 0)

        val header = ByteArray(5)
        samplePdf.inputStream().use { it.read(header) }
        assertEquals("%PDF-", String(header, Charsets.US_ASCII))
    }

    @Test
    fun `test sanitizePdfFile strips leading junk bytes before PDF header`() {
        val cleanPdf = File(testDir, "clean_source.pdf")
        createSamplePdf(cleanPdf)
        val cleanBytes = cleanPdf.readBytes()

        // Create corrupt PDF with random junk bytes / HTTP headers prepended
        val junkPrefix = "HTTP/1.1 200 OK\r\nContent-Type: application/pdf\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val corruptPdf = File(testDir, "corrupted_with_header.pdf")
        FileOutputStream(corruptPdf).use { fos ->
            fos.write(junkPrefix)
            fos.write(cleanBytes)
        }

        assertTrue(corruptPdf.length() > cleanPdf.length())

        // Sanitize the file
        val sanitized = FileImportHelper.sanitizePdfFile(corruptPdf)
        assertTrue("Sanitized file should exist", sanitized.exists())
        assertTrue("Sanitized file should have non-zero length", sanitized.length() > 0)

        // Verify the sanitized file starts with '%PDF-'
        val sanitizedHeader = ByteArray(5)
        sanitized.inputStream().use { it.read(sanitizedHeader) }
        assertEquals("%PDF-", String(sanitizedHeader, Charsets.US_ASCII))
    }

    @Test
    fun `test FileImportHelper correctly identifies Word and Office document types`() {
        assertTrue(FileImportHelper.isWordOrOfficeDocument("notes.docx", null))
        assertTrue(FileImportHelper.isWordOrOfficeDocument("syllabus.doc", null))
        assertTrue(FileImportHelper.isWordOrOfficeDocument("summary.dotx", null))
        assertTrue(FileImportHelper.isWordOrOfficeDocument("reading.txt", null))
        assertTrue(FileImportHelper.isWordOrOfficeDocument("article.rtf", null))
        assertTrue(FileImportHelper.isWordOrOfficeDocument("doc_file", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertTrue(FileImportHelper.isWordOrOfficeDocument("doc_file", "application/msword"))

        assertFalse(FileImportHelper.isWordOrOfficeDocument("notes.pdf", "application/pdf"))
        assertFalse(FileImportHelper.isWordOrOfficeDocument("image.png", "image/png"))
    }

    @Test
    fun `test FileImportHelper extracts URI from various intent configurations`() {
        val testUri = Uri.parse("content://com.android.providers.downloads/123")

        // 1. Data URI
        val intent1 = Intent(Intent.ACTION_VIEW).apply {
            data = testUri
        }
        assertEquals(testUri, FileImportHelper.extractUriFromIntent(intent1))

        // 2. Extra stream URI
        val intent2 = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, testUri)
        }
        assertEquals(testUri, FileImportHelper.extractUriFromIntent(intent2))

        // 3. Null intent safety
        assertNull(FileImportHelper.extractUriFromIntent(null))
    }

    @Test
    fun `test FileImportHelper resolves display name cleanly`() {
        val fileUri = Uri.parse("file:///storage/emulated/0/Download/Physics_Sem2_Notes.pdf")
        val resolvedName = FileImportHelper.resolveDisplayName(context, fileUri)
        assertEquals("Physics_Sem2_Notes.pdf", resolvedName)
    }

    @Test
    fun `test copyUriToLocal and importAndPrepareDocument handles local file scheme`() {
        val pdfFile = File(testDir, "direct_import.pdf")
        createSamplePdf(pdfFile)

        val uri = Uri.fromFile(pdfFile)
        val result = FileImportHelper.importAndPrepareDocument(context, uri, "direct_import.pdf")
        assertNotNull(result)
        val (readyFile, title) = result!!
        assertTrue(readyFile.exists())
        assertEquals("direct_import.pdf", title)

        val header = ByteArray(5)
        readyFile.inputStream().use { it.read(header) }
        assertEquals("%PDF-", String(header, Charsets.US_ASCII))
    }

    @Test
    fun `test WebScrollGuardState tracks touch and upward scroll stop state accurately`() {
        val guard = WebScrollGuardState()
        assertTrue(guard.isFirstSite)
        assertFalse(guard.isTouchActive)
        assertFalse(guard.lastScrollWasUp)

        // Switch to external site
        guard.isFirstSite = false
        guard.isTouchActive = true
        guard.lastScrollWasUp = true
        guard.lastScrollEventTime = 123456L

        assertFalse(guard.isFirstSite)
        assertTrue(guard.isTouchActive)
        assertTrue(guard.lastScrollWasUp)
        assertEquals(123456L, guard.lastScrollEventTime)
    }
}
