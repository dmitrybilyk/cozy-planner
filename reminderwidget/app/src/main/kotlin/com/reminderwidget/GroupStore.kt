package com.reminderwidget

import android.content.Context

object GroupStore {
    private const val PREFS       = "group_prefs"
    const val KEY_GROUP_ID        = "group_id"    // sanitized owner email used as Firestore doc ID
    const val KEY_GROUP_EMAIL     = "group_email" // human-readable owner email
    const val KEY_MY_NAME         = "my_name"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getGroupId(ctx: Context): String?    = prefs(ctx).getString(KEY_GROUP_ID, null)
    fun getGroupEmail(ctx: Context): String? = prefs(ctx).getString(KEY_GROUP_EMAIL, null)
    fun getMyName(ctx: Context): String?     = prefs(ctx).getString(KEY_MY_NAME, null)
    fun isInGroup(ctx: Context)              = getGroupId(ctx) != null

    fun emailToId(email: String): String =
        email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

    fun join(ctx: Context, ownerEmail: String, myName: String) {
        val groupId = emailToId(ownerEmail)
        prefs(ctx).edit()
            .putString(KEY_GROUP_ID,    groupId)
            .putString(KEY_GROUP_EMAIL, ownerEmail.trim())
            .putString(KEY_MY_NAME,     myName.trim())
            .apply()
    }

    fun leave(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_GROUP_ID)
            .remove(KEY_GROUP_EMAIL)
            .remove(KEY_MY_NAME)
            .apply()
    }
}
