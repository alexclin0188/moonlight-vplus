package com.alexclin.moonlink.android.stream

/**
 * Intent extra keys shared between new stream engine ([StreamEngine]) and old [com.limelight.Game].
 *
 * Extracted from [com.limelight.Game.Companion] to eliminate the hard dependency
 * on the legacy Game class. Both old and new code paths use the same key constants
 * so Intents built by one side can be consumed by the other without changes.
 */
object StreamIntentKeys {
    // ── Resolution reference ────────────────────────────────────────────────
    const val REFERENCE_HORIZ_RES = 1280
    const val REFERENCE_VERT_RES = 720

    // ── Intent extras ───────────────────────────────────────────────────────
    const val EXTRA_HOST = "Host"
    const val EXTRA_PORT = "Port"
    const val EXTRA_HTTPS_PORT = "HttpsPort"
    const val EXTRA_APP_NAME = "AppName"
    const val EXTRA_APP_ID = "AppId"
    const val EXTRA_UNIQUEID = "UniqueId"
    const val EXTRA_PC_UUID = "UUID"
    const val EXTRA_PC_NAME = "PcName"
    const val EXTRA_PAIR_NAME = "PairName"
    const val EXTRA_APP_HDR = "HDR"
    const val EXTRA_SERVER_CERT = "ServerCert"
    const val EXTRA_PC_USEVDD = "usevdd"
    const val EXTRA_APP_CMD = "CmdList"
    const val EXTRA_DISPLAY_NAME = "DisplayName"
    const val EXTRA_SCREEN_COMBINATION_MODE = "Screen combination mode"
    const val EXTRA_VDD_SCREEN_COMBINATION_MODE = "VDD screen combination mode"
    const val EXTRA_FORCE_RESUME_CURRENT_SESSION = "ForceResumeCurrentSession"

    // ── Shortcut trampoline extras (migrated from com.limelight.AppView) ──
    const val EXTRA_SHORTCUT_PC_NAME = "Name"
    const val EXTRA_SHORTCUT_PC_UUID = "UUID"
}
