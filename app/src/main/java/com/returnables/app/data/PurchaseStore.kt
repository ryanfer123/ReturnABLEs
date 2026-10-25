package com.returnables.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.returnables.app.domain.Purchase
import com.returnables.app.domain.PurchaseRules
import org.json.JSONObject

class PurchaseStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "returnables.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE purchases (id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE reminders (reminder_key TEXT PRIMARY KEY)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<Purchase> = readableDatabase.query("purchases", arrayOf("payload"), null, null, null, null, null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(PurchaseJson.decode(JSONObject(cursor.getString(0)))) }
    }
    fun save(purchase: Purchase) {
        val errors = PurchaseRules.validate(purchase)
        require(errors.isEmpty()) { errors.joinToString(" ") }
        val values = ContentValues().apply { put("id", purchase.id); put("payload", PurchaseJson.encode(purchase).toString()) }
        check(writableDatabase.insertWithOnConflict("purchases", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Purchase could not be saved." }
    }
    /** Restore adds missing IDs and preserves records already on this device. */
    fun restore(purchases: List<Purchase>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (purchase in purchases) {
                val values = ContentValues().apply { put("id", purchase.id); put("payload", PurchaseJson.encode(purchase).toString()) }
                if (db.insertWithOnConflict("purchases", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added
    }
    fun reminderWasSent(key: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM reminders WHERE reminder_key=?", arrayOf(key)).use { it.moveToFirst() }
    fun markReminderSent(key: String) { writableDatabase.insertWithOnConflict("reminders", null, ContentValues().apply { put("reminder_key", key) }, SQLiteDatabase.CONFLICT_IGNORE) }
}
