package com.alexclin.moonlink.android.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.util.ServerHelper
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URISyntaxException
import java.util.Collections

@Composable
fun AddComputerManuallyDialog(
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val dialogScope = rememberCoroutineScope()

    var hostInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-focus the text field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun doAdd() {
        val input = hostInput.trim()
        if (input.isEmpty()) {
            errorMessage = context.getString(R.string.addpc_enter_ip)
            return
        }
        if (managerBinder == null) return

        isLoading = true
        errorMessage = null

        dialogScope.launch(Dispatchers.IO) {
            try {
                val result = addPcBlocking(context, managerBinder, input)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    when {
                        result.isSuccess -> onSuccess(result.getOrThrow())
                        result.isFailure -> onError(extractErrorMessage(context, result.exceptionOrNull()))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    onError(context.getString(R.string.addpc_fail))
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .imePadding(),
            ) {
                // ── Title row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        context.getString(R.string.title_add_pc),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider()

                // ── Content ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            errorMessage = null
                        },
                        label = { Text(context.getString(R.string.ip_hint)) },
                        singleLine = true,
                        enabled = !isLoading,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                doAdd()
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )

                    Spacer(Modifier.height(12.dp))

                    // ── Bottom row: loading / buttons ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                context.getString(R.string.msg_add_pc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        TextButton(onClick = onDismiss, enabled = !isLoading) {
                            Text(context.getString(R.string.editor_cancel))
                        }

                        Spacer(Modifier.width(4.dp))

                        TextButton(onClick = { doAdd() }, enabled = !isLoading && hostInput.isNotBlank()) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(context.getString(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

// ── Core add-PC logic (extracted from AddComputerManually activity) ──

private suspend fun addPcBlocking(
    context: android.content.Context,
    managerBinder: ComputerManagerService.ComputerManagerBinder,
    rawUserInput: String,
): Result<String> = withContext(Dispatchers.IO) {
    val details = ComputerDetails()
    val uri = parseRawUserInputToUri(rawUserInput)

    if (uri == null || uri.host.isNullOrEmpty()) {
        return@withContext Result.failure(AddPcError.InvalidInput)
    }

    val host = uri.host ?: return@withContext Result.failure(AddPcError.InvalidInput)
    var port = uri.port
    if (port == -1) {
        port = NvHTTP.DEFAULT_HTTP_PORT
    }

    val isIPv6 = isIPv6Address(host)
    details.manualAddress = ComputerDetails.AddressTuple(host, port)
    val success = managerBinder.addComputerBlocking(details)

    if (!success) {
        if (isWrongSubnetSiteLocalAddress(host)) {
            return@withContext Result.failure(AddPcError.WrongSiteLocal)
        }

        // Test connectivity
        val portTestResult = MoonBridge.testClientConnectivity(
            ServerHelper.CONNECTION_TEST_SERVER, 443,
            MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989,
        )

        val errorMsg = if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
            context.getString(R.string.nettest_text_blocked)
        } else {
            var msg = context.getString(R.string.addpc_fail)
            if (isIPv6) {
                msg += "\n\n" + "提示：如果您使用的是IPv6地址，请检查：\n" +
                        "1. 光猫防火墙是否放行了IPv6流量\n" +
                        "2. 路由器是否启用了IPv6端口转发\n" +
                        "3. 目标主机的IPv6防火墙设置"
            }
            msg
        }
        return@withContext Result.failure(AddPcError.Other(errorMsg))
    }

    return@withContext Result.success(context.getString(R.string.addpc_success))
}

private sealed class AddPcError : Exception() {
    data object InvalidInput : AddPcError()
    data object WrongSiteLocal : AddPcError()
    data class Other(val errorMessage: String) : AddPcError()
}

private fun extractErrorMessage(context: android.content.Context, error: Throwable?): String {
    return when (error) {
        is AddPcError.InvalidInput -> context.getString(R.string.addpc_unknown_host)
        is AddPcError.WrongSiteLocal -> context.getString(R.string.addpc_wrong_sitelocal)
        is AddPcError.Other -> error.errorMessage
        else -> error?.message ?: context.getString(R.string.addpc_fail)
    }
}

// ── Utility functions (ported from AddComputerManually) ──

private fun isIPv6Address(address: String): Boolean {
    return try {
        val inetAddress = InetAddress.getByName(address)
        inetAddress.address.size == 16
    } catch (e: Exception) {
        address.count { it == ':' } >= 2
    }
}

private fun isWrongSubnetSiteLocalAddress(address: String): Boolean {
    try {
        val targetAddress = InetAddress.getByName(address)
        if (targetAddress !is Inet4Address || !targetAddress.isSiteLocalAddress) {
            return false
        }

        for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (addr in iface.interfaceAddresses) {
                if (addr.address !is Inet4Address || !addr.address.isSiteLocalAddress) {
                    continue
                }

                val targetAddrBytes = targetAddress.address
                val ifaceAddrBytes = addr.address.address

                var addressMatches = true
                for (i in 0 until addr.networkPrefixLength) {
                    if ((ifaceAddrBytes[i / 8].toInt() and (1 shl (i % 8))) !=
                        (targetAddrBytes[i / 8].toInt() and (1 shl (i % 8)))
                    ) {
                        addressMatches = false
                        break
                    }
                }

                if (addressMatches) {
                    return false
                }
            }
        }

        return true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

private fun parseRawUserInputToUri(rawUserInput: String): URI? {
    val colonCount = rawUserInput.count { it == ':' }
    val likelyIPv6 = colonCount >= 2

    try {
        val uri = URI("moonlight://$rawUserInput")
        if (!uri.host.isNullOrEmpty()) {
            return uri
        }
    } catch (ignored: URISyntaxException) {}

    if (likelyIPv6 && !rawUserInput.startsWith("[")) {
        try {
            val lastColonIndex = rawUserInput.lastIndexOf(':')

            var hasPort = false
            if (lastColonIndex > 0 && lastColonIndex < rawUserInput.length - 1) {
                val possiblePort = rawUserInput.substring(lastColonIndex + 1)
                try {
                    val port = possiblePort.toInt()
                    if (port in 1..65535) {
                        hasPort = true
                    }
                } catch (e: NumberFormatException) {}
            }

            val addressWithBrackets = if (hasPort) {
                val address = rawUserInput.substring(0, lastColonIndex)
                val port = rawUserInput.substring(lastColonIndex + 1)
                "[$address]:$port"
            } else {
                "[$rawUserInput]"
            }

            val uri = URI("moonlight://$addressWithBrackets")
            if (!uri.host.isNullOrEmpty()) {
                return uri
            }
        } catch (ignored: URISyntaxException) {}
    }

    if (!rawUserInput.startsWith("[")) {
        try {
            val uri = URI("moonlight://[$rawUserInput]")
            if (!uri.host.isNullOrEmpty()) {
                return uri
            }
        } catch (ignored: URISyntaxException) {}
    }

    return null
}
