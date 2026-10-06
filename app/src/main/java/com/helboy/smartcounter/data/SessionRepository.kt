package com.helboy.smartcounter.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionRepository(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_TOTAL INTEGER NOT NULL,
                $COL_FORWARD INTEGER NOT NULL,
                $COL_BACKWARD INTEGER NOT NULL,
                $COL_MODE TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun saveSession(total: Int, forward: Int, backward: Int, mode: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        val values = ContentValues().apply {
            put(COL_TIMESTAMP, timestamp)
            put(COL_TOTAL, total)
            put(COL_FORWARD, forward)
            put(COL_BACKWARD, backward)
            put(COL_MODE, mode)
        }

        return writableDatabase.insert(TABLE_SESSIONS, null, values)
    }

    fun getAllSessions(): List<CounterSession> {
        val list = mutableListOf<CounterSession>()
        val cursor = readableDatabase.query(
            TABLE_SESSIONS, null, null, null, null, null, "$COL_ID DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COL_ID))
                val ts = it.getString(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                val total = it.getInt(it.getColumnIndexOrThrow(COL_TOTAL))
                val forward = it.getInt(it.getColumnIndexOrThrow(COL_FORWARD))
                val backward = it.getInt(it.getColumnIndexOrThrow(COL_BACKWARD))
                val mode = it.getString(it.getColumnIndexOrThrow(COL_MODE))
                list.add(CounterSession(id, ts, total, forward, backward, mode))
            }
        }
        return list
    }

    fun exportCsv(): File? {
        val sessions = getAllSessions()
        val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        exportDir.mkdirs()

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val file = File(exportDir, "SmartCounter_Report_${sdf.format(Date())}.csv")

        return try {
            FileWriter(file).use { writer ->
                writer.append("ID,Timestamp,Total,Forward,Backward,Mode\n")
                for (s in sessions) {
                    writer.append("${s.id},\"${s.timestamp}\",${s.totalCount},${s.countForward},${s.countBackward},\"${s.mode}\"\n")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val DATABASE_NAME = "smart_counter.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SESSIONS = "sessions"
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_TOTAL = "total"
        private const val COL_FORWARD = "forward"
        private const val COL_BACKWARD = "backward"
        private const val COL_MODE = "mode"
    }
}
