package com.mtwabpin.kolahal.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Deletes the APK that `update install` kept, because the new version is now installed. */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateCommands.updatesDir(context).deleteRecursively()
    }
}
