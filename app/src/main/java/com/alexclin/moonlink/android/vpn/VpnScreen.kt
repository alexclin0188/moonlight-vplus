package com.alexclin.moonlink.android.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.easytier.jni.EasyTierJNI
import com.easytier.jni.EasyTierManager
import org.json.JSONObject

// ── Data classes (mirrors EasyTierController) ─────────────────────

private data class PeerInfo(
    val hostname: String,
    val virtualIp: String,
    val natType: String,
    val connectionDetail: String,
    val isDirect: Boolean,
    val isInSameSubnet: Boolean,
    val latency: String,
    val traffic: String,
)

private data class LocalNodeInfo(
    val hostname: String = "",
    val version: String = "",
    val virtualIp: String = "",
    val publicIp: String = "",
    val natType: String = "",
)

// ── Main VPN screen ───────────────────────────────────────────────

@Composable
fun VpnScreen(externalRefreshTrigger: Int) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    // ── State ──
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Status, 1=Config
    var isRunning by remember { mutableStateOf(false) }
    var statusJson by remember { mutableStateOf<String?>(null) }
    var localInfo by remember { mutableStateOf(LocalNodeInfo()) }
    var peers by remember { mutableStateOf<List<PeerInfo>>(emptyList()) }

    // Config form state
    var networkName by remember { mutableStateOf("easytier") }
    var networkSecret by remember { mutableStateOf("") }
    var localIpv4 by remember { mutableStateOf("10.0.0.1") }
    var listeners by remember { mutableStateOf("tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011") }
    var peersText by remember { mutableStateOf("tcp://public.easytier.top:11010\nudp://public.easytier.top:11010") }

    // Advanced flags
    var advancedExpanded by remember { mutableStateOf(false) }
    var useSmoltcp by remember { mutableStateOf(false) }
    var latencyFirst by remember { mutableStateOf(false) }
    var disableP2p by remember { mutableStateOf(false) }
    var privateMode by remember { mutableStateOf(false) }
    var disableIpv6 by remember { mutableStateOf(false) }
    var enableKcpProxy by remember { mutableStateOf(false) }
    var disableKcpInput by remember { mutableStateOf(false) }
    var enableQuicProxy by remember { mutableStateOf(false) }
    var disableQuicInput by remember { mutableStateOf(false) }
    var proxyForwardBySystem by remember { mutableStateOf(false) }
    var disableEncryption by remember { mutableStateOf(false) }
    var disableUdpHolePunching by remember { mutableStateOf(false) }
    var disableSymHolePunching by remember { mutableStateOf(false) }

    // EasyTierManager (lazy init)
    val easyTierManager = remember {
        val prefs = context.getSharedPreferences("easytier_preferences", Context.MODE_PRIVATE)
        val savedToml = prefs.getString("toml_config_string", null)
        val config = savedToml ?: buildDefaultToml()
        EasyTierManager(activity, "Default", config)
    }

    // Load saved config into form on first composition
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("easytier_preferences", Context.MODE_PRIVATE)
        val savedToml = prefs.getString("toml_config_string", null)
        if (savedToml != null) {
            networkName = extractValue(savedToml, "network_name", "easytier")
            networkSecret = extractValue(savedToml, "network_secret", "")
            localIpv4 = extractValue(savedToml, "ipv4", "10.0.0.1").removeSuffix("/24")
            listeners = extractListAsMultiline(savedToml, "listeners")
            peersText = extractPeerUris(savedToml)
        }
    }

    // VPN permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val config = buildTomlFromUi(
                networkName, networkSecret, localIpv4, listeners, peersText,
                useSmoltcp, latencyFirst, disableP2p, privateMode, disableIpv6,
                enableKcpProxy, disableKcpInput, enableQuicProxy, disableQuicInput,
                proxyForwardBySystem, disableEncryption, disableUdpHolePunching, disableSymHolePunching,
            )
            saveConfig(context, config)
            // Re-create manager with new config and start
            easyTierManager.stop()
            easyTierManager.start()
            isRunning = true
        }
    }

    // Refresh status
    fun refreshStatus() {
        statusJson = easyTierManager.latestNetworkInfoJson
        statusJson?.let { json ->
            parseStatusJson(json, "Default")?.let { (info, peerList) ->
                localInfo = info
                peers = peerList
            }
        }
    }

    // Observe external refresh trigger (from top bar Refresh button)
    LaunchedEffect(externalRefreshTrigger) {
        refreshStatus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
            // ── Tab buttons ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("网络状态") },
                    leadingIcon = if (selectedTab == 0) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("编辑配置") },
                    leadingIcon = if (selectedTab == 1) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }

            // ── Content ──
            if (selectedTab == 0) {
                // Status tab
                StatusTab(
                    localInfo = localInfo,
                    peers = peers,
                    isRunning = isRunning,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Config tab
                ConfigTab(
                    networkName = networkName, onNetworkNameChange = { networkName = it },
                    networkSecret = networkSecret, onNetworkSecretChange = { networkSecret = it },
                    localIpv4 = localIpv4, onLocalIpv4Change = { localIpv4 = it },
                    listeners = listeners, onListenersChange = { listeners = it },
                    peersText = peersText, onPeersTextChange = { peersText = it },
                    advancedExpanded = advancedExpanded,
                    onAdvancedToggle = { advancedExpanded = !advancedExpanded },
                    flags = AdvancedFlags(
                        useSmoltcp, latencyFirst, disableP2p, privateMode, disableIpv6,
                        enableKcpProxy, disableKcpInput, enableQuicProxy, disableQuicInput,
                        proxyForwardBySystem, disableEncryption, disableUdpHolePunching, disableSymHolePunching,
                    ),
                    onFlagChange = { flag, value ->
                        when (flag) {
                            "smoltcp" -> useSmoltcp = value
                            "latency" -> latencyFirst = value
                            "p2p" -> disableP2p = value
                            "private" -> privateMode = value
                            "ipv6" -> disableIpv6 = value
                            "kcp_proxy" -> enableKcpProxy = value
                            "kcp_input" -> disableKcpInput = value
                            "quic_proxy" -> enableQuicProxy = value
                            "quic_input" -> disableQuicInput = value
                            "proxy_system" -> proxyForwardBySystem = value
                            "encryption" -> disableEncryption = value
                            "udp_hole" -> disableUdpHolePunching = value
                            "sym_hole" -> disableSymHolePunching = value
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Bottom action buttons ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (isRunning) {
                            easyTierManager.stop()
                            isRunning = false
                        } else {
                            val vpnIntent = android.net.VpnService.prepare(activity)
                            if (vpnIntent != null) {
                                vpnPermissionLauncher.launch(vpnIntent)
                            } else {
                                val config = buildTomlFromUi(
                                    networkName, networkSecret, localIpv4, listeners, peersText,
                                    useSmoltcp, latencyFirst, disableP2p, privateMode, disableIpv6,
                                    enableKcpProxy, disableKcpInput, enableQuicProxy, disableQuicInput,
                                    proxyForwardBySystem, disableEncryption, disableUdpHolePunching, disableSymHolePunching,
                                )
                                saveConfig(context, config)
                                easyTierManager.start()
                                isRunning = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isRunning) "停止服务" else "启动服务")
                }
                OutlinedButton(
                    onClick = {
                        val config = buildTomlFromUi(
                            networkName, networkSecret, localIpv4, listeners, peersText,
                            useSmoltcp, latencyFirst, disableP2p, privateMode, disableIpv6,
                            enableKcpProxy, disableKcpInput, enableQuicProxy, disableQuicInput,
                            proxyForwardBySystem, disableEncryption, disableUdpHolePunching, disableSymHolePunching,
                        )
                        saveConfig(context, config)
                        android.widget.Toast.makeText(context, "配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存配置")
                }
            }
    }
}

// ── Status tab content ────────────────────────────────────────────

@Composable
private fun StatusTab(
    localInfo: LocalNodeInfo,
    peers: List<PeerInfo>,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isRunning && peers.isEmpty() && localInfo.hostname.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "服务未运行或正在连接…\n请点击刷新按钮获取最新状态。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Local node info
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("本机信息", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        InfoRow("主机名", localInfo.hostname)
                        InfoRow("虚拟 IP", localInfo.virtualIp)
                        InfoRow("公网 IP", localInfo.publicIp)
                        InfoRow("NAT 类型", localInfo.natType)
                    }
                }
            }

            // Peers
            if (peers.isNotEmpty()) {
                item {
                    Text(
                        "对等节点 (${peers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(peers) { peer ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    peer.hostname,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!peer.isDirect) {
                                    Text("(中转)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (!peer.isInSameSubnet) {
                                    Text("(网段不匹配!)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            InfoRow("虚拟 IP", peer.virtualIp)
                            InfoRow("NAT 类型", peer.natType)
                            InfoRow(if (peer.isDirect) "物理地址" else "下一跳", peer.connectionDetail)
                            InfoRow("延迟", peer.latency)
                            InfoRow("流量 (Rx/Tx)", peer.traffic)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Config tab content ────────────────────────────────────────────

private data class AdvancedFlags(
    val smoltcp: Boolean, val latency: Boolean, val p2p: Boolean, val private: Boolean, val ipv6: Boolean,
    val kcpProxy: Boolean, val kcpInput: Boolean, val quicProxy: Boolean, val quicInput: Boolean, val proxySystem: Boolean,
    val encryption: Boolean, val udpHole: Boolean, val symHole: Boolean,
)

@Composable
private fun ConfigTab(
    networkName: String, onNetworkNameChange: (String) -> Unit,
    networkSecret: String, onNetworkSecretChange: (String) -> Unit,
    localIpv4: String, onLocalIpv4Change: (String) -> Unit,
    listeners: String, onListenersChange: (String) -> Unit,
    peersText: String, onPeersTextChange: (String) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedToggle: () -> Unit,
    flags: AdvancedFlags,
    onFlagChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConfigField("网络名称", networkName, onNetworkNameChange, "例如 easytier")
        ConfigField("网络密钥", networkSecret, onNetworkSecretChange)
        ConfigField("本地虚拟 IPv4 地址", localIpv4, onLocalIpv4Change, "例如 10.0.0.6", KeyboardType.Number)
        ConfigMultilineField("监听地址", listeners, onListenersChange, "每行一个，例如 udp://0.0.0.0:11010")
        ConfigMultilineField("对等节点", peersText, onPeersTextChange, "每行一个，例如 tcp://1.2.3.4:11010")

        // Advanced flags collapsible
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAdvancedToggle),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("高级功能标志", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (advancedExpanded) 180f else 0f),
                    )
                }

                AnimatedVisibility(visible = advancedExpanded) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("核心网络行为", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlagSwitch("使用 Smoltcp", flags.smoltcp) { onFlagChange("smoltcp", it) }
                        FlagSwitch("延迟优先", flags.latency) { onFlagChange("latency", it) }
                        FlagSwitch("禁用 P2P（强制中转）", flags.p2p) { onFlagChange("p2p", it) }
                        FlagSwitch("私有模式", flags.private) { onFlagChange("private", it) }
                        FlagSwitch("禁用 IPv6", flags.ipv6) { onFlagChange("ipv6", it) }

                        Spacer(Modifier.height(8.dp))
                        Text("代理与协议", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlagSwitch("启用 KCP 代理", flags.kcpProxy) { onFlagChange("kcp_proxy", it) }
                        FlagSwitch("禁用 KCP 输入", flags.kcpInput) { onFlagChange("kcp_input", it) }
                        FlagSwitch("启用 QUIC 代理", flags.quicProxy) { onFlagChange("quic_proxy", it) }
                        FlagSwitch("禁用 QUIC 输入", flags.quicInput) { onFlagChange("quic_input", it) }
                        FlagSwitch("使用系统代理转发", flags.proxySystem) { onFlagChange("proxy_system", it) }

                        Spacer(Modifier.height(8.dp))
                        Text("安全与连接", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlagSwitch("禁用加密", flags.encryption) { onFlagChange("encryption", it) }
                        FlagSwitch("禁用 UDP 打洞", flags.udpHole) { onFlagChange("udp_hole", it) }
                        FlagSwitch("禁用对称 NAT 打洞", flags.symHole) { onFlagChange("sym_hole", it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigField(label: String, value: String, onChange: (String) -> Unit, hint: String = "", keyboardType: KeyboardType = KeyboardType.Text) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (hint.isNotEmpty()) {
            { Text(hint) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ConfigMultilineField(label: String, value: String, onChange: (String) -> Unit, hint: String = "") {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        placeholder = if (hint.isNotEmpty()) {
            { Text(hint) }
        } else null,
        minLines = 2,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun FlagSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── TOML utilities ────────────────────────────────────────────────

private fun saveConfig(context: Context, toml: String) {
    context.getSharedPreferences("easytier_preferences", Context.MODE_PRIVATE)
        .edit().putString("toml_config_string", toml).apply()
}

private fun buildDefaultToml() = """
[network_identity]
network_name = "easytier"
network_secret = ""

[instance_flags]
instance_name = "Default"
dhcp = false

[flags]
hostname = "moonlight-V+"

[network]
listeners = ["tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011"]
ipv4 = "10.0.0.1/24"

[[peer]]
uri = "tcp://public.easytier.top:11010"

[[peer]]
uri = "udp://public.easytier.top:11010"
""".trimIndent()

private fun buildTomlFromUi(
    name: String, secret: String, ipv4: String, listeners: String, peers: String,
    smoltcp: Boolean, latency: Boolean, p2p: Boolean, private: Boolean, ipv6: Boolean,
    kcpProxy: Boolean, kcpInput: Boolean, quicProxy: Boolean, quicInput: Boolean, proxySystem: Boolean,
    encryption: Boolean, udpHole: Boolean, symHole: Boolean,
): String {
    val sb = StringBuilder()
    sb.appendLine("[network_identity]")
    sb.appendLine("network_name = \"$name\"")
    sb.appendLine("network_secret = \"$secret\"")
    sb.appendLine()
    sb.appendLine("[instance_flags]")
    sb.appendLine("instance_name = \"Default\"")
    sb.appendLine("dhcp = false")
    sb.appendLine("hostname = \"moonlight-V+\"")
    sb.appendLine("rpc_portal = \"0.0.0.0:0\"")
    sb.appendLine()
    sb.appendLine("[network]")
    sb.appendLine("ipv4 = \"${ipv4}/24\"")

    val listenerList = listeners.lines().filter { it.isNotBlank() }
    sb.appendLine("listeners = [${listenerList.joinToString(", ") { "\"$it\"" }}]")
    sb.appendLine()

    peers.lines().filter { it.isNotBlank() }.forEach { uri ->
        sb.appendLine("[[peer]]")
        sb.appendLine("uri = \"$uri\"")
        sb.appendLine()
    }

    // Flags — only write non-default values
    sb.appendLine("[flags]")
    if (smoltcp) sb.appendLine("use_smoltcp = true")
    if (latency) sb.appendLine("latency_first = true")
    if (p2p) sb.appendLine("disable_p2p = true")
    if (private) sb.appendLine("private_mode = true")
    if (ipv6) sb.appendLine("enable_ipv6 = false")
    if (kcpProxy) sb.appendLine("enable_kcp_proxy = true")
    if (kcpInput) sb.appendLine("disable_kcp_input = true")
    if (quicProxy) sb.appendLine("enable_quic_proxy = true")
    if (quicInput) sb.appendLine("disable_quic_input = true")
    if (proxySystem) sb.appendLine("proxy_forward_by_system = true")
    if (encryption) sb.appendLine("enable_encryption = false")
    if (udpHole) sb.appendLine("disable_udp_hole_punching = true")
    if (symHole) sb.appendLine("disable_sym_hole_punching = true")

    return sb.toString()
}

private fun extractValue(toml: String, key: String, default: String): String {
    for (line in toml.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("$key")) {
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                return trimmed.substring(eqIdx + 1).trim().removeSurrounding("\"")
            }
        }
    }
    return default
}

private fun extractListAsMultiline(toml: String, key: String): String {
    val regex = Regex("""$key\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
    val match = regex.find(toml) ?: return ""
    return match.groupValues[1].split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }.joinToString("\n")
}

private fun extractPeerUris(toml: String): String {
    val regex = Regex("""uri\s*=\s*"([^"]*)"""")
    return regex.findAll(toml).map { it.groupValues[1] }.joinToString("\n")
}

// ── Status JSON parser ────────────────────────────────────────────

private fun parseStatusJson(json: String, instanceName: String): Pair<LocalNodeInfo, List<PeerInfo>>? {
    return try {
        val root = JSONObject(json)
        val instanceData = root.optJSONObject(instanceName) ?: return null

        // Local info
        val myNodeInfo = instanceData.optJSONObject("my_node_info") ?: return null
        val localInfo = LocalNodeInfo(
            hostname = myNodeInfo.optString("hostname", ""),
            version = myNodeInfo.optString("version", ""),
            virtualIp = myNodeInfo.optString("virtual_ipv4", ""),
            publicIp = myNodeInfo.optJSONObject("stun_info")?.optString("public_ip", "") ?: "",
            natType = parseNatType(myNodeInfo.optJSONObject("stun_info")?.optInt("udp_nat_type", 0) ?: 0),
        )

        // Parse routes and peers
        val routesArr = instanceData.optJSONArray("routes")
        val peersArr = instanceData.optJSONArray("peers")

        val routeMap = mutableMapOf<String, JSONObject>()
        if (routesArr != null) {
            for (i in 0 until routesArr.length()) {
                val r = routesArr.getJSONObject(i)
                val peerId = r.optString("peer_id", "")
                if (peerId.isNotEmpty()) routeMap[peerId] = r
            }
        }

        val peerConnMap = mutableMapOf<String, JSONObject>()
        if (peersArr != null) {
            for (i in 0 until peersArr.length()) {
                val p = peersArr.getJSONObject(i)
                val conn = p.optJSONObject("conns")
                if (conn != null) {
                    val peerId = conn.optString("peer_id", "")
                    if (peerId.isNotEmpty()) peerConnMap[peerId] = conn
                }
            }
        }

        val localIp = localInfo.virtualIp.substringBefore("/")
        val prefix = localInfo.virtualIp.substringAfter("/", "24").toIntOrNull() ?: 24

        val peerList = routeMap.map { (peerId, route) ->
            val conn = peerConnMap[peerId]
            val isDirect = conn != null
            val peerVirtualIp = route.optString("ipv4_addr", "")

            PeerInfo(
                hostname = route.optString("hostname", peerId.take(8)),
                virtualIp = peerVirtualIp,
                natType = parseNatType(route.optInt("nat_type", 0)),
                connectionDetail = if (isDirect) conn?.optString("physical_addr", "") ?: "" else route.optString("next_hop_peer_id", ""),
                isDirect = isDirect,
                isInSameSubnet = isInSameSubnet(localIp, peerVirtualIp, prefix),
                latency = conn?.optLong("latency_us", 0)?.let { if (it > 0) "${it / 1000}ms" else "—" } ?: "—",
                traffic = conn?.let {
                    val rx = it.optLong("rx_bytes", 0)
                    val tx = it.optLong("tx_bytes", 0)
                    "${formatBytes(rx)} / ${formatBytes(tx)}"
                } ?: "—",
            )
        }

        localInfo to peerList
    } catch (e: Exception) {
        null
    }
}

private fun parseNatType(code: Int): String = when (code) {
    0 -> "Unknown"
    1 -> "Full Cone"
    2 -> "Restricted Cone"
    3 -> "Port Restricted"
    4 -> "Symmetric"
    5 -> "Symmetric Easy"
    else -> "Type $code"
}

private fun isInSameSubnet(ip1: String, ip2: String, prefix: Int): Boolean {
    fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        if (parts.size != 4) return 0
        return parts.map { it.toIntOrNull() ?: 0 }.fold(0) { acc, v -> (acc shl 8) or (v and 0xFF) }
    }
    val mask = if (prefix >= 32) -1 else (-1 shl (32 - prefix))
    return (ipToInt(ip1) and mask) == (ipToInt(ip2) and mask)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0 / 1024.0)}MB"
    else -> "${"%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0)}GB"
}
