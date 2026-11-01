package com.mtwabpin.kolahal.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The new version is in, so the APK `update install` kept for it is done with. */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateCommands.updatesDir(context).deleteRecursively()
    }
}
