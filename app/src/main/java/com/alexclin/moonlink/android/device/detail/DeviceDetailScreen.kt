package com.alexclin.moonlink.android.device.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.device.overview.findComputer
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

@Composable
fun DeviceDetailScreen(
    uuid: String,
    computers: List<ComputerDetails>,
    managerBinder: ComputerManagerService.ComputerManagerBinder? = null,
    onBack: () -> Unit = {},
    editTrigger: Int = 0,
    copyTrigger: Int = 0,
) {
    val computer = findComputer(computers, uuid)
    if (computer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.title_device_not_found), style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editTrigger) {
        if (editTrigger > 0) showEditDialog = true
    }

    LaunchedEffect(copyTrigger) {
        if (copyTrigger > 0) {
            val detailText = deviceDetailsToText(computer)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.title_device_detail), detailText)
            clipboard.setPrimaryClip(clip)
            ToastUtil.show(context, context.getString(R.string.toast_details_copied), Toast.LENGTH_SHORT)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    if (showEditDialog) {
        EditRemoteAddressDialog(
            computer = computer,
            managerBinder = managerBinder,
            onDismiss = { showEditDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DetailContentCards(computer)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DetailContentCards(computer: ComputerDetails) {
    DetailCard {
        DetailRow(stringResource(R.string.label_device_name), computer.name ?: "—")
        DetailRow(stringResource(R.string.label_uuid), computer.uuid ?: "—")

        val stateText = when (computer.state) {
            ComputerDetails.State.ONLINE  -> stringResource(R.string.pcview_menu_header_online)
            ComputerDetails.State.OFFLINE -> stringResource(R.string.pcview_menu_header_offline)
            else -> stringResource(R.string.status_detecting)
        }
        DetailRow(stringResource(R.string.label_status), stateText)

        val pairText = when (computer.pairState) {
            PairingManager.PairState.PAIRED   -> stringResource(R.string.label_paired)
            PairingManager.PairState.NOT_PAIRED -> stringResource(R.string.label_not_paired)
            else -> "—"
        }
        DetailRow(stringResource(R.string.label_pair_state), pairText)

        if (computer.runningGameId != 0) {
            DetailRow(stringResource(R.string.label_running_app_id), computer.runningGameId.toString())
        }
    }

    DetailCard {
        SectionTitle(stringResource(R.string.section_network_info))
        DetailRow(stringResource(R.string.label_active_address), computer.activeAddress?.toString() ?: "—")
        DetailRow(stringResource(R.string.label_local_address), computer.localAddress?.toString() ?: "—")
        DetailRow(stringResource(R.string.label_remote_address), computer.remoteAddress?.toString() ?: "—")
        DetailRow(stringResource(R.string.label_manual_address), computer.manualAddress?.toString() ?: "—")
        DetailRow(stringResource(R.string.label_ipv6_address), computer.ipv6Address?.toString() ?: "—")
        DetailRow(stringResource(R.string.label_https_port), if (computer.httpsPort > 0) computer.httpsPort.toString() else "—")
        DetailRow(stringResource(R.string.label_mac_address), computer.macAddress ?: "—")
        DetailRow(stringResource(R.string.label_ipv6_status), if (computer.ipv6Disabled) stringResource(R.string.label_disabled) else stringResource(R.string.label_enabled))
    }

    DetailCard {
        SectionTitle(stringResource(R.string.section_sunshine_info))
        DetailRow(stringResource(R.string.label_sunshine_version), computer.getSunshineVersionDisplay().ifBlank { "—" })
        DetailRow(stringResource(R.string.label_nvidia_server), if (computer.nvidiaServer) stringResource(R.string.yes) else stringResource(R.string.no))
        DetailRow(stringResource(R.string.label_multi_address), if (computer.hasMultipleAddresses()) stringResource(R.string.yes) else stringResource(R.string.no))
        DetailRow(stringResource(R.string.label_desktop_quick_start), if (computer.supportsDesktopSpecialApp) stringResource(R.string.yes) else stringResource(R.string.no))
    }
}

@Composable
private fun EditRemoteAddressDialog(
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentAddr = computer.remoteAddress?.toString() ?: ""
    var addressText by remember { mutableStateOf(currentAddr) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_edit_remote_address)) },
        text = {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                label = { Text(stringResource(R.string.hint_host_port)) },
                placeholder = { Text(stringResource(R.string.placeholder_ip)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val text = addressText.trim()
                if (text.isNotEmpty()) {
                    val host: String
                    val port: Int
                    val colonIdx = text.lastIndexOf(':')
                    if (colonIdx > 0 && colonIdx > text.indexOf('[')) {
                        host = text.substring(0, colonIdx)
                        port = text.substring(colonIdx + 1).toIntOrNull() ?: fallbackPort(computer)
                    } else if (text.indexOf('[') != -1) {
                        host = text.trim('[', ']')
                        port = fallbackPort(computer)
                    } else {
                        host = text
                        port = fallbackPort(computer)
                    }
                    computer.remoteAddress = ComputerDetails.AddressTuple(host, port)
                    managerBinder?.updateComputer(computer)
                    ToastUtil.show(context, context.getString(R.string.toast_remote_address_updated), Toast.LENGTH_SHORT)
                }
                onDismiss()
            }) { Text(stringResource(R.string.dialog_button_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        },
    )
}

private fun fallbackPort(computer: ComputerDetails): Int {
    return computer.remoteAddress?.port
        ?: computer.localAddress?.port
        ?: computer.manualAddress?.port
        ?: 47989
}

private fun deviceDetailsToText(computer: ComputerDetails): String {
    val sb = StringBuilder()
    fun add(label: String, value: String) { sb.append("$label: $value\n") }
    add("Name", computer.name ?: "null")
    add("State", computer.state.toString())
    add("Active Address", computer.activeAddress?.toString() ?: "null")
    add("UUID", computer.uuid ?: "null")
    add("Local Address", computer.localAddress?.toString() ?: "null")
    add("Remote Address", computer.remoteAddress?.toString() ?: "null")
    add("IPv6 Address", if (computer.ipv6Disabled) "Disabled" else (computer.ipv6Address?.toString() ?: "null"))
    add("Manual Address", computer.manualAddress?.toString() ?: "null")
    add("MAC Address", computer.macAddress ?: "null")
    add("Pair State", computer.pairState.toString())
    if (computer.runningGameId != 0) add("Running Game ID", computer.runningGameId.toString())
    add("HTTPS Port", if (computer.httpsPort > 0) computer.httpsPort.toString() else "null")
    add("Sunshine Version", computer.getSunshineVersionDisplay())
    add("Desktop Special App Support", computer.supportsDesktopSpecialApp.toString())
    add("NVIDIA Server", computer.nvidiaServer.toString())
    return sb.toString().trimEnd()
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}
