package com.returnables.app

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.returnables.app.data.*
import com.returnables.app.domain.*
import com.returnables.app.reminders.DeadlineWorker
import com.returnables.app.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReturnViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app=application
    private val store=PurchaseStore(app)
    val receipts=ReceiptStore(app)
    private val mutable=MutableStateFlow(ReturnUiState())
    val state=mutable.asStateFlow()
    init { refresh(); restoreDraft() }
    private fun restoreDraft() = viewModelScope.launch {
        val name=savedState.get<String>("receiptDraft") ?: return@launch
        try {
            val draft=withContext(Dispatchers.IO) {
                receipts.file(name)
                val mime=when(name.substringAfterLast('.')) { "pdf"->"application/pdf";"jpg"->"image/jpeg";"png"->"image/png";else->"image/webp" }
                val cache=File(app.cacheDir,"draft-$name.txt")
                val text=if(cache.isFile)cache.readText().take(100_000) else ""
                ReceiptDraft(name,mime,text,ReceiptParser.suggest(text))
            }
            if(savedState.get<String>("receiptDraft")==name) mutable.update { it.copy(receiptDraft=draft) }
        } catch(error: Exception) {
            savedState.remove<String>("receiptDraft")
            message("The unsaved attachment could not be restored. Attach the receipt again before saving.")
        }
    }
    fun refresh() = viewModelScope.launch {
        try { val purchases=withContext(Dispatchers.IO) { store.all() }; mutable.update { it.copy(purchases=purchases,notificationsEnabled=DeadlineWorker.enabled(app)) } }
        catch (error: Exception) { message(error.message ?: "Your purchases could not be read.") }
    }
    fun message(value:String?) { mutable.update { it.copy(message=value) } }
    fun clearReceipt() {
        val name=savedState.remove<String>("receiptDraft")
        mutable.update { it.copy(receiptDraft=null) }
        if(name!=null && Regex("[0-9a-f]{64}\\.(jpg|png|webp|pdf)").matches(name)) viewModelScope.launch(Dispatchers.IO) { File(app.cacheDir,"draft-$name.txt").delete() }
    }
    private fun run(operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy=true) }
        viewModelScope.launch { try { operation() } catch(error: CancellationException) { throw error } catch(error: Exception) { message(error.message ?: "The operation could not be completed.") } finally { mutable.update { it.copy(busy=false) } } }
    }
    fun save(purchase:Purchase,complete:()->Unit) = run {
        withContext(Dispatchers.IO) {
            if(purchase.receiptName!=null) receipts.file(purchase.receiptName)
            store.save(purchase)
        }
        val purchases=withContext(Dispatchers.IO) { store.all() }
        mutable.update { it.copy(purchases=purchases,receiptDraft=null,message="Purchase saved on this device.") }
        clearReceipt()
        DeadlineWorker.checkNow(app)
        complete()
    }
    fun attach(uri:Uri)=run {
        val receipt=withContext(Dispatchers.IO) { receipts.import(uri) }
        var text="";var scanFailed=false
        if(receipt.mime.startsWith("image/")) {
            try { text=recognize(receipts.file(receipt.name)).take(100_000) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { scanFailed=true }
        }
        val suggestions=withContext(Dispatchers.Default) { ReceiptParser.suggest(text) }
        withContext(Dispatchers.IO) { File(app.cacheDir,"draft-${receipt.name}.txt").writeText(text) }
        savedState["receiptDraft"]=receipt.name
        mutable.update { it.copy(receiptDraft=ReceiptDraft(receipt.name,receipt.mime,text,suggestions),message=when {
            scanFailed -> "Receipt attached. Text could not be read; enter the details manually."
            receipt.mime=="application/pdf" -> "PDF attached. Enter its details manually; image receipts support text recognition."
            text.isBlank() -> "Receipt attached. No readable text found; enter the details manually."
            else -> "Receipt attached. Review suggestions before applying them."
        }) }
    }
    private suspend fun recognize(file:File):String {
        val bitmap=withContext(Dispatchers.IO) {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,bounds)
            var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2400)sample*=2
            val decoded=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("Image could not be decoded.")
            val orientation=runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix=Matrix().apply { when(orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f,1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f);postScale(-1f,1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f);postScale(-1f,1f) }
            } }
            if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also { if(it!==decoded)decoded.recycle() }
        }
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val task=try { recognizer.process(InputImage.fromBitmap(bitmap,0)) }
            catch(error:Exception) { recognizer.close();bitmap.recycle();throw error }
        return try { suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { result -> if(continuation.isActive)continuation.resume(result.text) }
                .addOnFailureListener { error -> if(continuation.isActive)continuation.resumeWithException(error) }
                .addOnCanceledListener { if(continuation.isActive)continuation.cancel() }
        } } finally {
            // ML Kit processing itself is not cancelled with the caller. Keep its bitmap alive until the task ends.
            if(task.isComplete) { recognizer.close();bitmap.recycle() }
            else task.addOnCompleteListener { recognizer.close();bitmap.recycle() }
        }
    }
    fun exportBackup(uri:Uri)=run {
        withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri,"wt")?.use { BackupArchive.write(it,store.all(),receipts.directory) } ?: error("Backup destination could not be opened.") }
        message("Backup exported with purchase records and receipt files. Keep it private.")
    }
    fun restore(uri:Uri)=run {
        val result=withContext(Dispatchers.IO) {
            val stage=File(app.cacheDir,"restore-${java.util.UUID.randomUUID()}").apply { mkdirs() }
            val newFiles=mutableListOf<File>()
            try {
                val backup=app.contentResolver.openInputStream(uri)?.use { BackupArchive.inspect(it,stage) } ?: error("Backup could not be opened.")
                for(source in backup.receiptFiles) {
                    val target=File(receipts.directory,source.name)
                    if(!target.exists()) {
                        check(source.renameTo(target)) { "Receipt could not be restored. Existing purchases were preserved." }
                        newFiles.add(target)
                    }
                }
                store.restore(backup.purchases) to backup.purchases.size
            } catch(error:Exception) { newFiles.forEach { it.delete() };throw error }
            finally { stage.deleteRecursively() }
        }
        val purchases=withContext(Dispatchers.IO) { store.all() }
        mutable.update { it.copy(purchases=purchases,message="Restored ${result.first} purchases. ${result.second-result.first} existing records kept unchanged.") }
        DeadlineWorker.checkNow(app)
    }
    fun evidence(purchase:Purchase,ready:(File)->Unit)=run {
        val file=withContext(Dispatchers.IO) {
            val folder=File(app.cacheDir,"exports").apply{mkdirs()}
            val file=File(folder,"returnABLEs-${java.util.UUID.randomUUID()}.zip")
            file.outputStream().use { EvidenceArchive.write(it,purchase,receipts.directory) };file
        }
        ready(file)
    }
    override fun onCleared() { store.close() }
}
