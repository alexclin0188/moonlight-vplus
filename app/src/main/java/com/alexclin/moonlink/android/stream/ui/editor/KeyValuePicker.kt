package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ════════════════════════════════════════════════════════════════════════════
//  键值数据模型
// ════════════════════════════════════════════════════════════════════════════

/**
 * 键值条目，包含内部值（tag）和显示名称（text）。
 */
data class KeyEntry(
    val value: String,   // 内部值，如 "k29", "m1", "g16"
    val label: String,   // 显示名称，如 "A", "ML", "start"
)

/**
 * 键值分组类别。
 */
private enum class KeyCategory(val label: String, val icon: ImageVector) {
    KEYBOARD("键盘", Icons.Default.Keyboard),
    MOUSE("鼠标", Icons.Default.Mouse),
    GAMEPAD("手柄", Icons.Default.SportsEsports),
    SPECIAL("特殊", Icons.Default.Keyboard),
}

// ════════════════════════════════════════════════════════════════════════════
//  完整键值列表（源自 page_device.xml）
// ════════════════════════════════════════════════════════════════════════════

/** 键盘按键列表，按 Windows VK 码排序 */
private val keyboardKeys = listOf(
    // ── 功能键行 ──
    KeyEntry("k111", "ESC"),
    KeyEntry("k131", "F1"),
    KeyEntry("k132", "F2"),
    KeyEntry("k133", "F3"),
    KeyEntry("k134", "F4"),
    KeyEntry("k135", "F5"),
    KeyEntry("k136", "F6"),
    KeyEntry("k137", "F7"),
    KeyEntry("k138", "F8"),
    KeyEntry("k139", "F9"),
    KeyEntry("k140", "F10"),
    KeyEntry("k141", "F11"),
    KeyEntry("k142", "F12"),
    KeyEntry("k124", "Ins"),
    KeyEntry("k112", "Del"),

    // ── 数字行 ──
    KeyEntry("k68", "~"),
    KeyEntry("k8", "1"),
    KeyEntry("k9", "2"),
    KeyEntry("k10", "3"),
    KeyEntry("k11", "4"),
    KeyEntry("k12", "5"),
    KeyEntry("k13", "6"),
    KeyEntry("k14", "7"),
    KeyEntry("k15", "8"),
    KeyEntry("k16", "9"),
    KeyEntry("k7", "0"),
    KeyEntry("k69", "-"),
    KeyEntry("k70", "="),
    KeyEntry("k67", "Back"),

    // ── QWERTY 行 ──
    KeyEntry("k61", "Tab"),
    KeyEntry("k45", "Q"),
    KeyEntry("k51", "W"),
    KeyEntry("k33", "E"),
    KeyEntry("k46", "R"),
    KeyEntry("k48", "T"),
    KeyEntry("k53", "Y"),
    KeyEntry("k49", "U"),
    KeyEntry("k37", "I"),
    KeyEntry("k43", "O"),
    KeyEntry("k44", "P"),
    KeyEntry("k71", "["),
    KeyEntry("k72", "]"),
    KeyEntry("k73", "\\"),

    // ── ASDF 行 ──
    KeyEntry("k115", "Cap"),
    KeyEntry("k29", "A"),
    KeyEntry("k47", "S"),
    KeyEntry("k32", "D"),
    KeyEntry("k34", "F"),
    KeyEntry("k35", "G"),
    KeyEntry("k36", "H"),
    KeyEntry("k38", "J"),
    KeyEntry("k39", "K"),
    KeyEntry("k40", "L"),
    KeyEntry("k74", ";"),
    KeyEntry("k75", "'"),
    KeyEntry("k66", "Enter"),

    // ── ZXCV 行 ──
    KeyEntry("k59", "Shift"),
    KeyEntry("k54", "Z"),
    KeyEntry("k52", "X"),
    KeyEntry("k31", "C"),
    KeyEntry("k50", "V"),
    KeyEntry("k30", "B"),
    KeyEntry("k42", "N"),
    KeyEntry("k41", "M"),
    KeyEntry("k55", ","),
    KeyEntry("k56", "."),
    KeyEntry("k76", "/"),
    KeyEntry("k60", "Shift"),

    // ── 底行 ──
    KeyEntry("k113", "Ctrl"),
    KeyEntry("k117", "Win"),
    KeyEntry("k57", "Alt"),
    KeyEntry("k62", "Space"),
    KeyEntry("k58", "Alt"),
    KeyEntry("k114", "Ctrl"),

    // ── 方向键 ──
    KeyEntry("k19", "↑"),
    KeyEntry("k20", "↓"),
    KeyEntry("k21", "←"),
    KeyEntry("k22", "→"),

    // ── 导航键 ──
    KeyEntry("k122", "Home"),
    KeyEntry("k123", "End"),
    KeyEntry("k92", "PgUp"),
    KeyEntry("k93", "PgDn"),

    // ── 小键盘 ──
    KeyEntry("k143", "NumLock"),
    KeyEntry("k151", "7"),
    KeyEntry("k148", "4"),
    KeyEntry("k145", "1"),
    KeyEntry("k144", "0"),
    KeyEntry("k154", "/"),
    KeyEntry("k152", "8"),
    KeyEntry("k149", "5"),
    KeyEntry("k146", "2"),
    KeyEntry("k155", "*"),
    KeyEntry("k153", "9"),
    KeyEntry("k150", "6"),
    KeyEntry("k147", "3"),
    KeyEntry("k156", "-"),
    KeyEntry("k157", "+"),
    KeyEntry("k158", "."),
)

/** 鼠标按键列表 */
private val mouseKeys = listOf(
    KeyEntry("m1", "左键"),
    KeyEntry("m2", "中键"),
    KeyEntry("m3", "右键"),
    KeyEntry("m4", "X1"),
    KeyEntry("m5", "X2"),
    KeyEntry("SU", "滚轮上"),
    KeyEntry("SD", "滚轮下"),
)

/** 手柄按键列表 */
private val gamepadKeys = listOf(
    KeyEntry("g1", "DPad上"),
    KeyEntry("g2", "DPad下"),
    KeyEntry("g4", "DPad左"),
    KeyEntry("g8", "DPad右"),
    KeyEntry("g16", "Start"),
    KeyEntry("g32", "Back"),
    KeyEntry("g64", "LSB"),
    KeyEntry("g128", "RSB"),
    KeyEntry("g256", "LB"),
    KeyEntry("g512", "RB"),
    KeyEntry("lt", "LT"),
    KeyEntry("rt", "RT"),
    KeyEntry("g4096", "A"),
    KeyEntry("g8192", "B"),
    KeyEntry("g16384", "X"),
    KeyEntry("g32768", "Y"),
)

/** 特殊功能键列表 */
private val specialKeys = listOf(
    KeyEntry("MMS", "鼠标移动"),
    KeyEntry("CMS", "鼠标滚轮模式"),
    KeyEntry("TPM", "触控板模式"),
    KeyEntry("MTM", "鼠标触控切换"),
    KeyEntry("MES", "鼠标编辑切换"),
    KeyEntry("PKS", "键盘模式"),
    KeyEntry("AKS", "摇杆模式"),
    KeyEntry("CSW", "滚轮切换"),
    KeyEntry("PZM", "缩放模式"),
    KeyEntry("OGM", "手柄模式"),
)

// ════════════════════════════════════════════════════════════════════════════
//  键值选择器对话框
// ════════════════════════════════════════════════════════════════════════════

/**
 * 键值快速选择弹窗，分选项卡展示键盘/鼠标/手柄/特殊按键。
 *
 * @param onSelect 选中键值时回调 (value: String, label: String)
 * @param onDismiss 关闭弹窗
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyValuePickerDialog(
    onSelect: (value: String, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val categories = KeyCategory.entries

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxSize(0.95f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 标题 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("选择键值", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(20.dp))
                    }
                }

                // ── 选项卡 ──
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.height(36.dp),
                ) {
                    categories.forEachIndexed { index, cat ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier.padding(vertical = 0.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Icon(cat.icon, contentDescription = null,
                                    modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(cat.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── 按键网格 ──
                val keys = when (selectedTab) {
                    0 -> keyboardKeys
                    1 -> mouseKeys
                    2 -> gamepadKeys
                    3 -> specialKeys
                    else -> keyboardKeys
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 键盘用 FlowRow 展示
                    if (selectedTab == 0) {
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                keys.forEach { key ->
                                    KeyChip(
                                        key = key,
                                        onClick = { onSelect(key.value, key.label) },
                                    )
                                }
                            }
                        }
                    } else {
                        // 其他类别：4列网格展示
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                keys.chunked(4).forEach { rowKeys ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        rowKeys.forEach { key ->
                                            GridKeyItem(
                                                key = key,
                                                onClick = { onSelect(key.value, key.label) },
                                                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                            )
                                        }
                                        repeat(4 - rowKeys.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  辅助组件
// ════════════════════════════════════════════════════════════════════════════

/** 小芯片按钮（用于键盘流式布局） */
@Composable
private fun KeyChip(
    key: KeyEntry,
    onClick: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            key.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 网格项（用于鼠标/手柄/特殊，标签 + 键值，等高等宽） */
@Composable
private fun GridKeyItem(
    key: KeyEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                key.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                key.value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  名称查找工具
// ════════════════════════════════════════════════════════════════════════════

/** 根据内部值（tag）查找对应的显示名称 */
fun getKeyLabelByValue(value: String): String? {
    val allKeys = keyboardKeys + mouseKeys + gamepadKeys + specialKeys
    return allKeys.firstOrNull { it.value == value }?.label
}
