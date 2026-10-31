package com.mtwabpin.kolahal.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            File(context.cacheDir, "updates").deleteRecursively()
        }
    }
}
