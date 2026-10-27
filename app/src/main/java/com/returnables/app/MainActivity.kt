package com.returnables.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.CalendarContract
import com.returnables.app.domain.CalendarDraft
import com.returnables.app.domain.DeadlineKind
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.returnables.app.domain.Purchase
import com.returnables.app.reminders.DeadlineWorker
import com.returnables.app.ui.ReturnActions
import com.returnables.app.ui.ReturnablesApp
import java.io.File

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ReturnViewModel::class.java] }
    private var captureUri:Uri?=null
    private val attachmentPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.attach(it) } }
    private val capture=registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri=captureUri;captureUri=null
        if(success && uri!=null)model.attach(uri)
    }
    private val backupDestination=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { model.exportBackup(it) } }
    private val restoreSource=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.restore(it) } }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.refresh()
        if(granted) { DeadlineWorker.checkNow(this);model.message("Deadline reminders enabled. Android may delay background delivery.") }
        else model.message("Notifications are off. Deadlines remain visible in the app; enable notifications in Android settings when ready.")
    }
    private val actions=object:ReturnActions {
        override fun save(purchase:Purchase,onComplete:()->Unit) { model.save(purchase,onComplete) }
        override fun attachReceipt() { attachmentPicker.launch(arrayOf("image/jpeg","image/png","image/webp","application/pdf")) }
        override fun captureReceipt() {
            try {
                val file=File(File(cacheDir,"capture").apply{mkdirs()},"receipt-${java.util.UUID.randomUUID()}.jpg")
                val uri=FileProvider.getUriForFile(this@MainActivity,packageName+".files",file)
                captureUri=uri;capture.launch(uri)
            } catch (_:ActivityNotFoundException) { model.message("No camera app is available. Attach a receipt image instead.") }
        }
        override fun clearReceiptDraft() { model.clearReceipt() }
        override fun openReceipt(purchase:Purchase) {
            try {
                val uri=model.receipts.uri(purchase.receiptName ?: error("No receipt is attached."))
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,purchase.receiptMime ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_:ActivityNotFoundException) { model.message("No viewer is installed for this file. Export the evidence to open it on another device.") }
            catch(error:Exception) { model.message(error.message ?: "Receipt could not be opened.") }
        }
        override fun shareEvidence(purchase:Purchase) { model.evidence(purchase) { file ->
            val uri=FileProvider.getUriForFile(this@MainActivity,packageName+".files",file)
            try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="application/zip";putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newRawUri("Purchase evidence",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },"Share purchase evidence")) } catch (_:ActivityNotFoundException) { model.message("No sharing app is installed.") }
        } }
        override fun addToCalendar(purchase:Purchase,kind:DeadlineKind) {
            val draft=CalendarDraft.from(purchase,kind) ?: run { model.message("This deadline cannot be exported to your calendar. Check the date and purchase status.");return }
            try {
                startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
                    putExtra(CalendarContract.Events.TITLE,draft.title)
                    putExtra(CalendarContract.Events.DESCRIPTION,draft.description)
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,true)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,draft.beginMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME,draft.endMillis)
                })
            } catch (_:ActivityNotFoundException) { model.message("No calendar app is installed. Your confirmed date is still saved in returnABLEs.") }
        }
        override fun exportBackup() { backupDestination.launch("returnABLEs-backup-${java.time.LocalDate.now()}.zip") }
        override fun restoreBackup() { restoreSource.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed")) }
        override fun requestNotifications() {
            if(DeadlineWorker.enabled(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            } else if(Build.VERSION.SDK_INT>=33) {
                if(shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))model.message("Allow notifications to receive return and warranty reminders. Your purchase list also shows all deadlines.")
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if(!DeadlineWorker.enabled(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            } else { DeadlineWorker.checkNow(this@MainActivity);model.message("Notifications are enabled. Reminders check deadlines within three days; delivery may be delayed by Android.") }
        }
        override fun clearMessage() { model.message(null) }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        captureUri=savedInstanceState?.getString("captureUri")?.let(Uri::parse)
        setContent { val state by model.state.collectAsStateWithLifecycle();ReturnablesApp(state,actions) }
    }
    override fun onResume() { super.onResume();model.refresh() }
    override fun onSaveInstanceState(outState:Bundle) { captureUri?.let { outState.putString("captureUri",it.toString()) };super.onSaveInstanceState(outState) }
}
