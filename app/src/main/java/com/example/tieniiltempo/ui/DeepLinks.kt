package com.example.tieniiltempo.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.tieniiltempo.LauncherActivity

object DeepLinks {
    fun openRunner(context: Context, activityId: String) {
        val i = Intent(context, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("deeplink", "runner")
            putExtra("activityId", activityId)
        }
        ContextCompat.startActivity(context, i, null)
    }
}
