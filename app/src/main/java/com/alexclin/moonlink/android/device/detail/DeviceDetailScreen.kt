package com.alexclin.moonlink.android.device.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.device.overview.findComputer
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

@Composable
fun DeviceDetailScreen(
    uuid: String,
    computers: List<ComputerDetails>,
    onBack: () -> Unit = {},
) {
    val computer = findComputer(computers, uuid)
    if (computer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("设备未找到", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

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

        computer.activeAddress?.let {
            DetailRow("当前活跃地址", it.toString())
        }
        computer.localAddress?.let {
            DetailRow("局域网地址", it.toString())
        }
        computer.remoteAddress?.let {
            DetailRow("远程地址", it.toString())
        }
        computer.manualAddress?.let {
            DetailRow("手动地址", it.toString())
        }
        computer.ipv6Address?.let {
            DetailRow("IPv6 地址", it.toString())
        }
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
    }
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
