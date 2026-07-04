package com.limelight.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object HelpLauncher {
    fun launchUrl(context: Context, url: String) {
        // Try to launch the default browser.
        // On leanback devices without a browser this will throw and the exception
        // is silently swallowed (the old WebView helper activity has been removed).
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            context.startActivity(i)
        } catch (_: Exception) {
            // No browser available — silently ignore
        }
    }

    fun launchSetupGuide(context: Context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide")
    }

    fun launchTroubleshooting(context: Context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/Troubleshooting")
    }

    fun launchGameStreamEolFaq(context: Context) {
        launchUrl(context, "https://github.com/moonlight-stream/moonlight-docs/wiki/NVIDIA-GameStream-End-Of-Service-Announcement-FAQ")
    }
}
