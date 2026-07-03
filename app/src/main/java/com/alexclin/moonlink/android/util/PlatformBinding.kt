package com.alexclin.moonlink.android.util

import android.content.Context

import com.alexclin.moonlink.android.util.AndroidCryptoProvider
import com.limelight.nvstream.http.LimelightCryptoProvider

object PlatformBinding {
    @JvmStatic
    fun getCryptoProvider(c: Context): LimelightCryptoProvider {
        return AndroidCryptoProvider(c)
    }
}
