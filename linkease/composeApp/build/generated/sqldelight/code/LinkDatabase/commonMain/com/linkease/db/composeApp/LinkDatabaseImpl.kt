package com.linkease.db.composeApp

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.linkease.db.LinkDatabase
import com.linkease.db.SessionQueries
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<LinkDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = LinkDatabaseImpl.Schema

internal fun KClass<LinkDatabase>.newInstance(driver: SqlDriver): LinkDatabase =
    LinkDatabaseImpl(driver)

private class LinkDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), LinkDatabase {
  override val sessionQueries: SessionQueries = SessionQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS SessionRecord (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    date TEXT NOT NULL,
          |    clientName TEXT NOT NULL,
          |    time TEXT NOT NULL,
          |    notes TEXT NOT NULL DEFAULT ''
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
