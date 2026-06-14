package com.linkease.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.linkease.db.composeApp.newInstance
import com.linkease.db.composeApp.schema
import kotlin.Unit

public interface LinkDatabase : Transacter {
  public val sessionQueries: SessionQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = LinkDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): LinkDatabase =
        LinkDatabase::class.newInstance(driver)
  }
}
