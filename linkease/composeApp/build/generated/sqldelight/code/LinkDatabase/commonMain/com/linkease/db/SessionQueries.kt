package com.linkease.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class SessionQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAll(mapper: (
    id: Long,
    date: String,
    clientName: String,
    time: String,
    notes: String,
  ) -> T): Query<T> = Query(1_693_782_180, arrayOf("SessionRecord"), driver, "Session.sq",
      "selectAll",
      "SELECT SessionRecord.id, SessionRecord.date, SessionRecord.clientName, SessionRecord.time, SessionRecord.notes FROM SessionRecord") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!
    )
  }

  public fun selectAll(): Query<SessionRecord> = selectAll { id, date, clientName, time, notes ->
    SessionRecord(
      id,
      date,
      clientName,
      time,
      notes
    )
  }

  public fun lastInsertRowId(): ExecutableQuery<Long> = Query(-1_438_659_771, driver, "Session.sq",
      "lastInsertRowId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insert(
    date: String,
    clientName: String,
    time: String,
    notes: String,
  ) {
    driver.execute(-1_827_829_382, """
        |INSERT INTO SessionRecord(date, clientName, time, notes)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindString(0, date)
          bindString(1, clientName)
          bindString(2, time)
          bindString(3, notes)
        }
    notifyQueries(-1_827_829_382) { emit ->
      emit("SessionRecord")
    }
  }

  public fun deleteById(id: Long) {
    driver.execute(93_259_358, """DELETE FROM SessionRecord WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(93_259_358) { emit ->
      emit("SessionRecord")
    }
  }
}
