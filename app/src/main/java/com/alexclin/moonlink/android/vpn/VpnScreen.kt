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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.easytier.jni.EasyTierJNI
import com.easytier.jni.EasyTierManager
import org.json.JSONObject
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import com.alexclin.moonlink.android.R

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

@Composable
fun VpnScreen(externalRefreshTrigger: Int) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var selectedTab by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var statusJson by remember { mutableStateOf<String?>(null) }
    var localInfo by remember { mutableStateOf(LocalNodeInfo()) }
    var peers by remember { mutableStateOf<List<PeerInfo>>(emptyList()) }

    var networkName by remember { mutableStateOf("easytier") }
    var networkSecret by remember { mutableStateOf("") }
    var localIpv4 by remember { mutableStateOf("10.0.0.1") }
    var listeners by remember { mutableStateOf("tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011") }
    var peersText by remember { mutableStateOf("tcp://public.easytier.top:11010\nudp://public.easytier.top:11010") }

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

    val easyTierManager = remember {
        val prefs = context.getSharedPreferences("easytier_preferences", Context.MODE_PRIVATE)
        val savedToml = prefs.getString("toml_config_string", null)
        val config = savedToml ?: buildDefaultToml()
        EasyTierManager(activity, "Default", config)
    }

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
            easyTierManager.stop()
            easyTierManager.start()
            isRunning = true
        }
    }

    fun refreshStatus() {
        statusJson = easyTierManager.latestNetworkInfoJson
        statusJson?.let { json ->
            parseStatusJson(json, "Default")?.let { (info, peerList) ->
                localInfo = info
                peers = peerList
            }
        }
    }

    LaunchedEffect(externalRefreshTrigger) { refreshStatus() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(stringResource(R.string.tab_network_status)) },
                leadingIcon = if (selectedTab == 0) {
                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null,
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(stringResource(R.string.tab_edit_config)) },
                leadingIcon = if (selectedTab == 1) {
                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null,
            )
        }

        if (selectedTab == 0) {
            StatusTab(localInfo = localInfo, peers = peers, isRunning = isRunning, modifier = Modifier.weight(1f))
        } else {
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
                Text(if (isRunning) stringResource(R.string.btn_stop_service) else stringResource(R.string.btn_start_service))
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
                    ToastUtil.show(context, context.getString(R.string.toast_config_saved), Toast.LENGTH_SHORT)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.btn_save_config))
            }
        }
    }
}

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
                stringResource(R.string.label_service_not_running),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.label_local_info), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(stringResource(R.string.label_hostname), localInfo.hostname)
                        InfoRow(stringResource(R.string.label_virtual_ip), localInfo.virtualIp)
                        InfoRow(stringResource(R.string.label_public_ip), localInfo.publicIp)
                        InfoRow(stringResource(R.string.label_nat_type), localInfo.natType)
                    }
                }
            }

            if (peers.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.label_peers) + " (${peers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(peers) { peer ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(peer.hostname, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                if (!peer.isDirect) Text(stringResource(R.string.label_relay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                if (!peer.isInSameSubnet) Text(stringResource(R.string.label_subnet_mismatch), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(4.dp))
                            InfoRow(stringResource(R.string.label_virtual_ip), peer.virtualIp)
                            InfoRow(stringResource(R.string.label_nat_type), peer.natType)
                            InfoRow(if (peer.isDirect) stringResource(R.string.label_physical_addr) else stringResource(R.string.label_next_hop), peer.connectionDetail)
                            InfoRow(stringResource(R.string.label_latency), peer.latency)
                            InfoRow(stringResource(R.string.label_traffic), peer.traffic)
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConfigField(stringResource(R.string.label_network_name), networkName, onNetworkNameChange, stringResource(R.string.hint_network_name))
        ConfigField(stringResource(R.string.label_network_secret), networkSecret, onNetworkSecretChange)
        ConfigField(stringResource(R.string.label_local_ipv4), localIpv4, onLocalIpv4Change, stringResource(R.string.hint_local_ipv4), KeyboardType.Number)
        ConfigMultilineField(stringResource(R.string.label_listeners), listeners, onListenersChange, stringResource(R.string.hint_listeners))
        ConfigMultilineField(stringResource(R.string.label_peers_config), peersText, onPeersTextChange, stringResource(R.string.hint_peers))

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
                    Text(stringResource(R.string.label_advanced_flags), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(if (advancedExpanded) 180f else 0f))
                }

                AnimatedVisibility(visible = advancedExpanded) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.section_core_network), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlagSwitch(stringResource(R.string.flag_use_smoltcp), flags.smoltcp) { onFlagChange("smoltcp", it) }
                        FlagSwitch(stringResource(R.string.flag_latency_first), flags.latency) { onFlagChange("latency", it) }
                        FlagSwitch(stringResource(R.string.flag_disable_p2p), flags.p2p) { onFlagChange("p2p", it) }
                        FlagSwitch(stringResource(R.string.flag_private_mode), flags.private) { onFlagChange("private", it) }
                        FlagSwitch(stringResource(R.string.flag_disable_ipv6), flags.ipv6) { onFlagChange("ipv6", it) }

                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.section_proxy_protocol), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlagSwitch(stringResource(R.string.flag_enable_kcp_proxy), flags.kcpProxy) { onFlagChange("kcp_proxy", it) }
                        FlagSwitch(stringResource(R.string.flag_disable_kcp_input), flags.kcpInput) { onFlagChange("kcp_input", it) }
                        FlagSwitch(stringResource(R.string.flag_enable_quic_proxy), flags.quicProxy) { onFlagChange("quic_proxy", it) }
                        FlagSwitch(stringResource(R.string.flag_disable_quic_input), flags.quicInput) { onFlagChange("quic_input", it) }
                        FlagSwitch(stringResource(R.string.flag_proxy_forward_system), flags.proxySystem) { onFlagChange("proxy_system", it) }

                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.section_security), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlagSwitch(stringResource(R.string.flag_disable_encryption), flags.encryption) { onFlagChange("encryption", it) }
                        FlagSwitch(stringResource(R.string.flag_disable_udp_hole), flags.udpHole) { onFlagChange("udp_hole", it) }
                        FlagSwitch(stringResource(R.string.flag_disable_sym_hole), flags.symHole) { onFlagChange("sym_hole", it) }
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
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        placeholder = if (hint.isNotEmpty()) { { Text(hint) } } else null,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ConfigMultilineField(label: String, value: String, onChange: (String) -> Unit, hint: String = "") {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        placeholder = if (hint.isNotEmpty()) { { Text(hint) } } else null,
        minLines = 2, shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun FlagSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── TOML utilities ──

private fun saveConfig(context: Context, toml: String) {
    context.getSharedPreferences("easytier_preferences", Context.MODE_PRIVATE).edit().putString("toml_config_string", toml).apply()
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
        sb.appendLine("[[peer]]"); sb.appendLine("uri = \"$uri\""); sb.appendLine()
    }
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
            if (eqIdx > 0) return trimmed.substring(eqIdx + 1).trim().removeSurrounding("\"")
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

private fun parseStatusJson(json: String, instanceName: String): Pair<LocalNodeInfo, List<PeerInfo>>? {
    return try {
        val root = JSONObject(json)
        val instanceData = root.optJSONObject(instanceName) ?: return null
        val myNodeInfo = instanceData.optJSONObject("my_node_info") ?: return null
        val localInfo = LocalNodeInfo(
            hostname = myNodeInfo.optString("hostname", ""),
            version = myNodeInfo.optString("version", ""),
            virtualIp = myNodeInfo.optString("virtual_ipv4", ""),
            publicIp = myNodeInfo.optJSONObject("stun_info")?.optString("public_ip", "") ?: "",
            natType = parseNatType(myNodeInfo.optJSONObject("stun_info")?.optInt("udp_nat_type", 0) ?: 0),
        )

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
                traffic = conn?.let { val rx = it.optLong("rx_bytes", 0); val tx = it.optLong("tx_bytes", 0); "${formatBytes(rx)} / ${formatBytes(tx)}" } ?: "—",
            )
        }
        localInfo to peerList
    } catch (e: Exception) { null }
}

private fun parseNatType(code: Int): String = when (code) {
    0 -> "Unknown"; 1 -> "Full Cone"; 2 -> "Restricted Cone"; 3 -> "Port Restricted"; 4 -> "Symmetric"; 5 -> "Symmetric Easy"; else -> "Type $code"
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
