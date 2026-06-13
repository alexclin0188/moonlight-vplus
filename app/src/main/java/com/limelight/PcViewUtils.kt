package com.limelight

import androidx.annotation.VisibleForTesting
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLException

/** Translates network/coroutine exceptions into user-friendly messages.
 *  Extracted from [PcView] for testability. */
@VisibleForTesting
fun friendlyNetworkError(e: Throwable, getString: () -> String): String {
    // Unwrap ExecutionException to get the real cause
    val cause = when (e) {
        is ExecutionException -> e.cause ?: e
        else -> e
    }
    return when (cause) {
        is UnknownHostException,
        is SocketException,
        is SocketTimeoutException,
        is ConnectException,
        is NoRouteToHostException,
        is SSLException ->
            getString()
        else -> cause.javaClass.simpleName
    }
}
