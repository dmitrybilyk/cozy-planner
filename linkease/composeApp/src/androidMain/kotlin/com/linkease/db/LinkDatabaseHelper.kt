package com.linkease.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LinkDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "linkease.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE clients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                colorHex TEXT NOT NULL DEFAULT '#2196F3',
                hourlyRate REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                address TEXT NOT NULL DEFAULT '',
                colorHex TEXT NOT NULL DEFAULT '#4CAF50'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                startTime TEXT NOT NULL,
                endTime TEXT NOT NULL,
                locationId INTEGER,
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE session_clients (
                sessionId INTEGER NOT NULL,
                clientId INTEGER NOT NULL,
                PRIMARY KEY (sessionId, clientId)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE availability (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                startTime TEXT NOT NULL,
                endTime TEXT NOT NULL,
                locationId INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS session_clients")
            db.execSQL("DROP TABLE IF EXISTS sessions")
            db.execSQL("DROP TABLE IF EXISTS clients")
            db.execSQL("DROP TABLE IF EXISTS locations")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS availability (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dayOfWeek INTEGER NOT NULL,
                    startTime TEXT NOT NULL,
                    endTime TEXT NOT NULL,
                    locationId INTEGER
                )
            """.trimIndent())
        }
        if (oldVersion < 4) {
            // Migrate availability from dayOfWeek to date-specific slots.
            // Old data is not convertible (no target date), so we drop and recreate.
            db.execSQL("DROP TABLE IF EXISTS availability")
            db.execSQL("""
                CREATE TABLE availability (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date TEXT NOT NULL,
                    startTime TEXT NOT NULL,
                    endTime TEXT NOT NULL,
                    locationId INTEGER
                )
            """.trimIndent())
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE clients ADD COLUMN hourlyRate REAL NOT NULL DEFAULT 0")
        }
    }
}
