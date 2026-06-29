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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.device.overview.findComputer
import com.limelight.computers.ComputerManagerService
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
            Text("设备未找到", style = MaterialTheme.typography.titleLarge)
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
            val clip = ClipData.newPlainText("设备详情", detailText)
            clipboard.setPrimaryClip(clip)
            ToastUtil.show(context, "详情已复制到剪贴板", Toast.LENGTH_SHORT)
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

    if (isLandscape) {
        // ── 横屏：全宽可滚动内容 ─────────────────────
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailContentCards(computer)
            Spacer(Modifier.height(16.dp))
        }
    } else {
        // ── 竖屏：原始布局 ────────────────────────────
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
}

// ── Shared content cards ──────────────────────────────────────────

@Composable
private fun DetailContentCards(computer: ComputerDetails) {
    // ── Basic info card ─────────────────────────
    DetailCard {
        DetailRow("设备名称", computer.name ?: "—")
        DetailRow("UUID", computer.uuid ?: "—")

        val stateText = when (computer.state) {
            ComputerDetails.State.ONLINE  -> "在线"
            ComputerDetails.State.OFFLINE -> "离线"
            else -> "检测中…"
        }
        DetailRow("状态", stateText)

        val pairText = when (computer.pairState) {
            PairingManager.PairState.PAIRED   -> "已配对"
            PairingManager.PairState.NOT_PAIRED -> "未配对"
            else -> "—"
        }
        DetailRow("配对状态", pairText)

        if (computer.runningGameId != 0) {
            DetailRow("运行中应用 ID", computer.runningGameId.toString())
        }
    }

    // ── Network info card ───────────────────────
    DetailCard {
        SectionTitle("网络信息")

        DetailRow("当前活跃地址", computer.activeAddress?.toString() ?: "—")
        DetailRow("局域网地址", computer.localAddress?.toString() ?: "—")
        DetailRow("远程地址", computer.remoteAddress?.toString() ?: "—")
        DetailRow("手动地址", computer.manualAddress?.toString() ?: "—")
        DetailRow("IPv6 地址", computer.ipv6Address?.toString() ?: "—")
        DetailRow("HTTPS 端口", if (computer.httpsPort > 0) computer.httpsPort.toString() else "—")
        DetailRow("MAC 地址", computer.macAddress ?: "—")
        DetailRow("IPv6", if (computer.ipv6Disabled) "已禁用" else "已启用")
    }

    // ── Sunshine info card ──────────────────────
    DetailCard {
        SectionTitle("Sunshine 信息")
        DetailRow("Sunshine 版本", computer.getSunshineVersionDisplay().ifBlank { "—" })
        DetailRow("NVIDIA Server", if (computer.nvidiaServer) "是" else "否")
        DetailRow("支持多地址", if (computer.hasMultipleAddresses()) "是" else "否")
        DetailRow("支持桌面快速启动", if (computer.supportsDesktopSpecialApp) "是" else "否")
    }
}

// ── Edit remote address dialog ─────────────────────────────────────

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
        title = { Text("编辑远程地址") },
        text = {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                label = { Text("host:port") },
                placeholder = { Text("192.168.1.100:47989") },
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
                    ToastUtil.show(context, "远程地址已更新", Toast.LENGTH_SHORT)
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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

// ── Reusable components ───────────────────────────────────────────

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
