package com.reminderwidget

import android.content.Context

object ProState {
    const val KEY_MOCK       = "dev_mock_state"
    const val MOCK_AUTO      = -1
    const val MOCK_FREE      = 0
    const val MOCK_PRO       = 1

    private const val KEY_FIRST_INSTALL = "first_install_ms"
    const val TRIAL_DAYS = 7L

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (p.getLong(KEY_FIRST_INSTALL, 0L) == 0L)
            p.edit().putLong(KEY_FIRST_INSTALL, System.currentTimeMillis()).apply()
    }

    fun isUnlocked(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        return when (p.getInt(KEY_MOCK, MOCK_AUTO)) {
            MOCK_PRO  -> true
            MOCK_FREE -> false
            else      -> isInTrial(ctx) || BillingManager.isSubscribed
        }
    }

    fun isInTrial(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val first = p.getLong(KEY_FIRST_INSTALL, 0L)
        if (first == 0L) return true
        return (System.currentTimeMillis() - first) < TRIAL_DAYS * 24 * 3_600_000L
    }

    fun trialDaysLeft(ctx: Context): Int {
        val p = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val first = p.getLong(KEY_FIRST_INSTALL, 0L).takeIf { it > 0L } ?: return TRIAL_DAYS.toInt()
        val left  = TRIAL_DAYS - (System.currentTimeMillis() - first) / (24 * 3_600_000L)
        return left.coerceIn(0, TRIAL_DAYS).toInt()
    }

    fun getMockState(ctx: Context): Int =
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).getInt(KEY_MOCK, MOCK_AUTO)

    fun setMockState(ctx: Context, state: Int) {
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MOCK, state).apply()
    }
}
