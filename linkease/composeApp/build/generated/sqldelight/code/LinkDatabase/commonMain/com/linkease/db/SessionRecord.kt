package com.linkease.db

import kotlin.Long
import kotlin.String

public data class SessionRecord(
  public val id: Long,
  public val date: String,
  public val clientName: String,
  public val time: String,
  public val notes: String,
)
