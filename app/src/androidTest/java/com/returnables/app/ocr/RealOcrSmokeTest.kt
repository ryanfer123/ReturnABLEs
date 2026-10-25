package com.returnables.app.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.returnables.app.domain.ReceiptParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Synthetic, clean test image: one integration smoke check, not an accuracy benchmark. */
@RunWith(AndroidJUnit4::class)
class RealOcrSmokeTest {
    @Test fun bundledRecognizerReadsFixtureAndProducesSourceLinkedSuggestions() {
        assertNotSame("Tasks.await must not block the Android main thread.",Looper.getMainLooper(),Looper.myLooper())
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("The application must not request internet permission.",permissions.contains(Manifest.permission.INTERNET))
        val bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.BLACK
            textSize=72f
            typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL)
        }
        // Test data only; this is not a merchant logo or a real customer's receipt.
        listOf("PAPER TRAIL STORE","Date 2026-10-10","Headphones","TOTAL INR 1299.00","SYNTHETIC TEST RECEIPT").forEachIndexed { index,line ->
            canvas.drawText(line,90f,150f+index*150f,paint)
        }
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val task=recognizer.process(InputImage.fromBitmap(bitmap,0))
        try {
            val text=Tasks.await(task,30,TimeUnit.SECONDS).text
            val normalized=text.uppercase(Locale.ROOT).replace(Regex("\\s+")," ")
            assertTrue("Merchant missing from actual OCR response: $text",normalized.contains("PAPER TRAIL STORE"))
            assertTrue("Date missing from actual OCR response: $text",normalized.contains("2026-10-10"))
            assertTrue("Total missing from actual OCR response: $text",normalized.contains("INR 1299.00"))
            val suggestions=ReceiptParser.suggest(text)
            assertEquals("PAPER TRAIL STORE",suggestions.merchantGuess)
            assertTrue("Parsed date must retain the recognized source line.",suggestions.dateCandidates.any {
                it.date==LocalDate.of(2026,10,10) && it.sourceLine.contains("2026-10-10") && !it.ambiguous
            })
            assertTrue("Parsed INR total must retain the recognized source line.",suggestions.totalCandidates.any {
                it.minor==129900L && it.currency=="INR" && it.sourceLine.uppercase(Locale.ROOT).contains("TOTAL") && it.sourceLine.contains("1299.00")
            })
        } finally {
            // A timeout must not recycle pixels while the recognizer still uses them.
            if(task.isComplete) { recognizer.close();bitmap.recycle() }
            else task.addOnCompleteListener { recognizer.close();bitmap.recycle() }
        }
    }
}
