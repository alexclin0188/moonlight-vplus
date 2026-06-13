package com.alexclin.moonlink.home

import android.content.Context
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import java.security.PrivateKey
import java.security.cert.X509Certificate
import com.limelight.utils.ServerHelper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [handleQrPairResult] and the [PairQrResult] sealed class.
 *
 * Uses JUnit 4 + MockK. Android SDK stubs are mocked via [mockkStatic]
 * / [mockkObject]; [NvHTTP] and [AndroidCryptoProvider] construction are
 * intercepted via [mockkConstructor].
 *
 * Test scenarios:
 * 1. managerBinder is null                     → PairQrResult.Error
 * 2. addComputerBlocking returns false          → PairQrResult.Error
 * 3. Already paired (getPairState() == PAIRED)  → PairQrResult.Success  (short‑circuit)
 * 4. Successful pairing (pair() returns PAIRED) → PairQrResult.Success  + side‑effects
 * 5. PIN wrong (pair() returns PIN_WRONG)       → PairQrResult.Error with PIN message
 * 6. Pairing failure (pair() returns FAILED)    → PairQrResult.Error with generic message
 */
class PairingHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockBinder: ComputerManagerService.ComputerManagerBinder

    @Before
    fun setUp() {
        // Context is an abstract class in the Android SDK — relaxed = true avoids
        // AbstractMethodError when MockK intercepts final / concrete stub methods.
        mockContext = mockk(relaxed = true)
        mockBinder = mockk()

        // ── Context ────────────────────────────────────────────────────────
        every { mockContext.getString(any<Int>()) } returns "default string"
        every { mockContext.contentResolver } returns mockk(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)

        // ── Binder ─────────────────────────────────────────────────────────
        every { mockBinder.getUniqueId() } returns "test-unique-id"
        every { mockBinder.invalidateStateForComputer(any()) } just Runs

        // ── ServerHelper (object) ──────────────────────────────────────────
        mockkObject(ServerHelper)
        every { ServerHelper.getCurrentAddressFromComputer(any()) } returns
            ComputerDetails.AddressTuple("192.168.1.100", 47989)

        // ── Android static stubs ───────────────────────────────────────────
        // OkHttp's Platform detector calls Log.isLoggable on Android — without
        // Robolectric this throws "not mocked".  Mock it so the NvHTTP
        // constructor can complete without crashing during platform detection.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.isLoggable(any<String>(), any<Int>()) } returns false
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0

        mockkStatic(android.provider.Settings.Global::class)
        every { android.provider.Settings.Global.getString(any(), any()) } returns "TestDevice"

        // ── AndroidCryptoProvider constructor mock ─────────────────────────
        // PlatformBinding.getCryptoProvider() calls new AndroidCryptoProvider().
        // mockkConstructor intercepts the constructor, but the resulting mock
        // is spy-like: unmocked method calls fall through to the real
        // implementation.  PairingManager's constructor will call
        // getClientPrivateKey / getClientCertificate / getPemEncodedClientCertificate
        // on this instance, so we explicitly stub those methods.
        mockkConstructor(AndroidCryptoProvider::class)
        every { anyConstructed<AndroidCryptoProvider>().getClientPrivateKey() } returns
            mockk<PrivateKey>(relaxed = true)
        every { anyConstructed<AndroidCryptoProvider>().getClientCertificate() } returns
            mockk<X509Certificate>(relaxed = true)
        every { anyConstructed<AndroidCryptoProvider>().getPemEncodedClientCertificate() } returns
            byteArrayOf()

        // ── NvHTTP constructor mock ────────────────────────────────────────
        // mockkConstructor does NOT suppress the class initialiser.  The real
        // init { … } block executes, which among other things calls:
        //   this.pairingManager = PairingManager(this, cryptoProvider)
        // We therefore also mock the PairingManager constructor so that the
        // backing field receives a mock PairingManager (see below).
        mockkConstructor(NvHTTP::class)
        every { anyConstructed<NvHTTP>().getPairState() } returns
            PairingManager.PairState.NOT_PAIRED
        // getServerInfo() is called by the function as argument to pm.pair().
        // It must be mocked so the argument expression doesn't make a real
        // HTTP connection (the PairingManager mock ignores the content).
        every { anyConstructed<NvHTTP>().getServerInfo(any()) } returns
            "<?xml version=\"1.0\"?><root><appversion>7.1.234</appversion></root>"

        // ── PairingManager constructor mock ────────────────────────────────
        // NvHTTP's init creates a real PairingManager.  Without intercepting
        // that constructor the test would make real HTTP calls during
        // pm.pair().  By mocking the constructor here the NvHTTP init will
        // store a mock PairingManager in its backing field, and subsequent
        // access to httpConn.pairingManager will return that mock.
        // (val-property getters — including pairingManager — are NOT
        // intercepted by mockkConstructor, so the field value is what sticks.)
        mockkConstructor(PairingManager::class)
    }

    @After
    fun tearDown() {
        // clearAllMocks() also clears constructor mocks; avoid it to prevent
        // destabilising mockkConstructor between test runs.
        unmockkAll()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. managerBinder is null → Error(addpc_fail)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult returns error when managerBinder is null`() = runBlocking {
        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "1234",
            port = 47989,
            managerBinder = null
        )

        assertTrue("Expected Error when managerBinder is null", result is PairQrResult.Error)
        assertEquals(
            "Error message should be the add-PC-failure string",
            mockContext.getString(com.limelight.R.string.addpc_fail),
            (result as PairQrResult.Error).message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. addComputerBlocking returns false → Error(addpc_fail)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult returns error when addComputer fails`() = runBlocking {
        every { mockBinder.addComputerBlocking(any()) } returns false

        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "1234",
            port = 47989,
            managerBinder = mockBinder
        )

        assertTrue("Expected Error when addComputerBlocking fails", result is PairQrResult.Error)
        assertEquals(
            mockContext.getString(com.limelight.R.string.addpc_fail),
            (result as PairQrResult.Error).message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Already paired (getPairState() == PAIRED) → Success (short-circuit)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult returns success when already paired`() = runBlocking {
        every { mockBinder.addComputerBlocking(any()) } answers {
            firstArg<ComputerDetails>().uuid = "test-uuid"
            true
        }
        every { mockBinder.getComputer(any()) } returns null
        // Override the default NOT_PAIRED → PAIRED
        every { anyConstructed<NvHTTP>().getPairState() } returns PairingManager.PairState.PAIRED

        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "1234",
            port = 47989,
            managerBinder = mockBinder
        )

        assertTrue(
            "Expected Success when getPairState() == PAIRED",
            result is PairQrResult.Success
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Successful pairing (pair() returns PAIRED) → Success + side-effects
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult returns success when pairing succeeds and updates computer`() = runBlocking {
        // Use a real ComputerDetails so we can verify mutations
        val returnedComputer = ComputerDetails()
        returnedComputer.uuid = "test-uuid"

        every { mockBinder.addComputerBlocking(any()) } answers {
            firstArg<ComputerDetails>().uuid = "test-uuid"
            true
        }
        // First getComputer call returns the computer (non-null → used for NvHTTP)
        every { mockBinder.getComputer(any()) } returns returnedComputer
        every { anyConstructed<NvHTTP>().getPairState() } returns PairingManager.PairState.NOT_PAIRED
        every { anyConstructed<PairingManager>().pair(any(), any()) } returns
            PairingManager.PairResult(PairingManager.PairState.PAIRED, "test-pair-name")

        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "1234",
            port = 47989,
            managerBinder = mockBinder
        )

        assertTrue("Expected Success after successful pair", result is PairQrResult.Success)

        // Verify side-effects
        verify {
            mockBinder.invalidateStateForComputer("test-uuid")
        }
        assertEquals(
            "Computer pairState should be updated to PAIRED",
            PairingManager.PairState.PAIRED,
            returnedComputer.pairState
        )
        // serverCert was set to pm.pairedCert (null in the test)
        assertNull("Computer serverCert should be set to pairedCert", returnedComputer.serverCert)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. PIN wrong (pair() returns PIN_WRONG) → Error(pair_incorrect_pin)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult returns error when pin is wrong`() = runBlocking {
        every { mockBinder.addComputerBlocking(any()) } answers {
            firstArg<ComputerDetails>().uuid = "test-uuid"
            true
        }
        every { mockBinder.getComputer(any()) } returns null
        every { anyConstructed<NvHTTP>().getPairState() } returns PairingManager.PairState.NOT_PAIRED
        every { anyConstructed<PairingManager>().pair(any(), any()) } returns
            PairingManager.PairResult(PairingManager.PairState.PIN_WRONG, null)

        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "wrong-pin",
            port = 47989,
            managerBinder = mockBinder
        )

        assertTrue("Expected Error when PIN is wrong", result is PairQrResult.Error)
        assertEquals(
            "Error message should indicate incorrect PIN",
            mockContext.getString(com.limelight.R.string.pair_incorrect_pin),
            (result as PairQrResult.Error).message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Pairing failure (pair() returns FAILED) → Error(pair_fail)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult returns error when pairing fails generically`() = runBlocking {
        every { mockBinder.addComputerBlocking(any()) } answers {
            firstArg<ComputerDetails>().uuid = "test-uuid"
            true
        }
        every { mockBinder.getComputer(any()) } returns null
        every { anyConstructed<NvHTTP>().getPairState() } returns PairingManager.PairState.NOT_PAIRED
        every { anyConstructed<PairingManager>().pair(any(), any()) } returns
            PairingManager.PairResult(PairingManager.PairState.FAILED, null)

        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "1234",
            port = 47989,
            managerBinder = mockBinder
        )

        assertTrue("Expected Error on generic pairing failure", result is PairQrResult.Error)
        assertEquals(
            "Error message should indicate pairing failure",
            mockContext.getString(com.limelight.R.string.pair_fail),
            (result as PairQrResult.Error).message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Additional edge: default port when port is null
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handleQrPairResult uses default port when port is null`() = runBlocking {
        every { mockBinder.addComputerBlocking(any()) } answers {
            val details = firstArg<ComputerDetails>()
            details.uuid = "test-uuid"
            assertEquals(
                "Default NvHTTP port should be used",
                NvHTTP.DEFAULT_HTTP_PORT, details.manualAddress!!.port
            )
            true
        }
        every { mockBinder.getComputer(any()) } returns null
        every { anyConstructed<NvHTTP>().getPairState() } returns PairingManager.PairState.PAIRED

        val result = handleQrPairResult(
            context = mockContext,
            host = "192.168.1.100",
            pin = "1234",
            port = null,
            managerBinder = mockBinder
        )

        assertTrue("Expected Success with default port", result is PairQrResult.Success)
    }
}
