package com.limelight

import org.junit.Test
import org.junit.Assert.assertEquals
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLException

/** Unit tests for [friendlyNetworkError] — the network error translation function. */
class PcViewTest {

    private val networkErrorMessage = "Network error. Please check your connection."

    @Test
    fun friendlyNetworkError_unknownHostException() {
        val result = friendlyNetworkError(UnknownHostException()) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_socketException() {
        val result = friendlyNetworkError(SocketException()) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_socketTimeoutException() {
        val result = friendlyNetworkError(SocketTimeoutException()) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_connectException() {
        val result = friendlyNetworkError(ConnectException()) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_noRouteToHostException() {
        val result = friendlyNetworkError(NoRouteToHostException()) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_sslException() {
        val result = friendlyNetworkError(SSLException("SSL handshake failed")) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_executionExceptionWrappingSocketException() {
        val socketException = SocketException("connection reset")
        val executionException = ExecutionException(socketException)
        val result = friendlyNetworkError(executionException) { networkErrorMessage }
        assertEquals(networkErrorMessage, result)
    }

    @Test
    fun friendlyNetworkError_genericException() {
        val result = friendlyNetworkError(Exception("something")) { networkErrorMessage }
        assertEquals("Exception", result)
    }

    @Test
    fun friendlyNetworkError_executionExceptionWithNullCause() {
        val executionException = ExecutionException("wrapper", null)
        val result = friendlyNetworkError(executionException) { networkErrorMessage }
        assertEquals("ExecutionException", result)
    }
}
