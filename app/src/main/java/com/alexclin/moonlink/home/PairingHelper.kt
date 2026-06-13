package com.alexclin.moonlink.home

import android.content.Context
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.utils.ServerHelper

sealed class PairQrResult {
    data object Success : PairQrResult()
    data class Error(val message: String) : PairQrResult()
}

suspend fun handleQrPairResult(
    context: Context,
    host: String,
    pin: String,
    port: Int?,
    managerBinder: ComputerManagerService.ComputerManagerBinder?
): PairQrResult {
    val actualPort = port ?: NvHTTP.DEFAULT_HTTP_PORT

    val addDetails = ComputerDetails()
    addDetails.manualAddress = ComputerDetails.AddressTuple(host, actualPort)
    val added = managerBinder?.addComputerBlocking(addDetails) == true
    if (!added) {
        return PairQrResult.Error(context.getString(R.string.addpc_fail))
    }

    var computer = managerBinder?.getComputer(addDetails.uuid!!)
    if (computer == null) computer = addDetails

    val httpConn = NvHTTP(
        ServerHelper.getCurrentAddressFromComputer(computer),
        computer.httpsPort,
        managerBinder?.getUniqueId() ?: "",
        android.provider.Settings.Global.getString(
            context.contentResolver, "device_name"
        ) ?: android.os.Build.MODEL ?: "MoonLink Client",
        computer.serverCert,
        PlatformBinding.getCryptoProvider(context)
    )

    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
        return PairQrResult.Success
    }

    val pm = httpConn.pairingManager
    val pairResult = pm.pair(
        httpConn.getServerInfo(true), pin
    )

    return when (pairResult.state) {
        PairingManager.PairState.PAIRED -> {
            managerBinder?.getComputer(addDetails.uuid!!)?.let { c ->
                c.serverCert = pm.pairedCert
                c.pairState = PairingManager.PairState.PAIRED
            }
            pairResult.pairName?.let { name ->
                context.getSharedPreferences("pair_name_map", Context.MODE_PRIVATE)
                    .edit().putString(addDetails.uuid, name).apply()
            }
            managerBinder?.invalidateStateForComputer(addDetails.uuid!!)
            PairQrResult.Success
        }
        PairingManager.PairState.PIN_WRONG -> PairQrResult.Error(context.getString(R.string.pair_incorrect_pin))
        else -> PairQrResult.Error(context.getString(R.string.pair_fail))
    }
}
