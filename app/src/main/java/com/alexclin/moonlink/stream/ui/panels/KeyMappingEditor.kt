package com.alexclin.moonlink.stream.ui.panels

import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.limelight.binding.input.advance_setting.element.ElementController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper

// ════════════════════════════════════════════════════════════
//  T-08: 全屏编辑器 Compose 壳
// ════════════════════════════════════════════════════════════

/**
 * 按键映射全屏编辑器（T-08 / T-09 / T-10）。
 *
 * 顶部工具栏 + 元素编辑画布区域 + 底栏网格宽度滑块。
 * 当旧 [ElementController] 桥接可用时，通过 AndroidView 嵌入其 View；
 * 暂不可用时显示引导提示。
 */
@Composable
fun KeyMappingEditor(
    engine: StreamEngine,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    // ── 状态 ──
    var schemeName by remember { mutableStateOf("按键方案") }
    var isEditingName by remember { mutableStateOf(false) }
    var gridWidth by remember { mutableIntStateOf(8) }
    var showAddMenu by remember { mutableStateOf(false) }

    // 尝试加载当前方案名
    LaunchedEffect(Unit) {
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val currentId = prefs.getLong("current_config_id", 0L)
            if (currentId != 0L) {
                val db = SuperConfigDatabaseHelper(context)
                val name = db.queryConfigAttribute(currentId, "config_name", "按键方案") as? String
                if (!name.isNullOrBlank()) schemeName = name
            }
        } catch (_: Exception) { }
    }

    // 进入编辑模式（桥接可用时）
    val elementController = engine.controllerManager?.elementController
    LaunchedEffect(elementController) {
        elementController?.let { ec ->
            try {
                ec.changeMode(ElementController.Mode.Edit)
            } catch (_: Exception) { }
        }
    }

    // 退出编辑器
    val exitEditor: () -> Unit = {
        elementController?.let { ec ->
            try {
                ec.changeMode(ElementController.Mode.Normal)
            } catch (_: Exception) { }
        }
        onClose()
    }

    // ── 页面主体 ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ════════════════════════════════════════
            // 顶栏（T-08：工具栏）
            // ════════════════════════════════════════
            EditorToolbar(
                schemeName = schemeName,
                isEditingName = isEditingName,
                onStartEditName = { isEditingName = true },
                onNameChange = { schemeName = it },
                onConfirmName = { isEditingName = false },
                onAddElement = { showAddMenu = true },
                onAddComboKey = {
                    Toast.makeText(context, "组合键编辑（待实现）", Toast.LENGTH_SHORT).show()
                },
                onExit = exitEditor,
            )

            // ════════════════════════════════════════
            // 中间：元素编辑画布区域（T-09）
            // ════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (elementController != null) {
                    ElementEditorCanvas(elementController = elementController!!)
                } else {
                    BridgePlaceholder()
                }
            }

            // ════════════════════════════════════════
            // 底栏：网格宽度滑块（T-10）
            // ════════════════════════════════════════
            GridWidthSlider(
                gridWidth = gridWidth,
                onGridWidthChange = { gridWidth = it },
            )
        }

        // ── 添加元素菜单 ──
        if (showAddMenu) {
            AddElementMenu(
                onDismiss = { showAddMenu = false },
                onSelect = { _ ->
                    showAddMenu = false
                    elementController?.let { ec ->
                        try {
                            ec.changeMode(ElementController.Mode.Edit)
                            Toast.makeText(context, "已切换编辑模式，请在画布上放置元素", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "添加元素失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  元素编辑画布（T-09 核心）
// ════════════════════════════════════════════════════════════

@Composable
private fun ElementEditorCanvas(elementController: ElementController) {
    AndroidView(
        factory = { ctx: android.content.Context ->
            try {
                // 通过 getElements() 获取已有元素列表
                val elements = elementController.elements
                // 创建一个容器显示元素列表预览（当旧系统 View 不可嵌入时）
                val container = FrameLayout(ctx).apply {
                    setBackgroundColor(0x33000000)
                }
                // 添加提示信息
                val hint = android.widget.TextView(ctx).apply {
                    text = "编辑器桥接已连接，当前 ${elements?.size ?: 0} 个元素"
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                }
                container.addView(hint)
                container
            } catch (e: Exception) {
                placeholderView(ctx, "编辑器加载失败: ${e.message}")
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BridgePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "编辑器桥接未就绪",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "旧 Crown 元素编辑器 (ElementController) 尚不可用。\n请先在 Game.kt 中完成 ControllerManager 初始化桥接。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  编辑器工具栏（T-08）
// ════════════════════════════════════════════════════════════

@Composable
private fun EditorToolbar(
    schemeName: String,
    isEditingName: Boolean,
    onStartEditName: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirmName: () -> Unit,
    onAddElement: () -> Unit,
    onAddComboKey: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ← 保存退出
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "保存退出",
                    tint = MaterialTheme.colorScheme.primary)
            }

            // 方案名称（可编辑）
            if (isEditingName) {
                BasicTextField(
                    value = schemeName,
                    onValueChange = { if (it.length <= 10) onNameChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                IconButton(onClick = onConfirmName) {
                    Icon(Icons.Default.Check, contentDescription = "确认",
                        tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Text(
                    schemeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStartEditName() }
                        .padding(horizontal = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onStartEditName) {
                    Icon(Icons.Default.BorderColor, contentDescription = "编辑名称",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 分隔
            HorizontalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // 添加按键
            TextButton(onClick = onAddElement) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("按键", style = MaterialTheme.typography.labelMedium)
            }

            // 组合键
            TextButton(onClick = onAddComboKey) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("组合键", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  网格宽度滑块（T-10）
// ════════════════════════════════════════════════════════════

@Composable
private fun GridWidthSlider(
    gridWidth: Int,
    onGridWidthChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.GridOn, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("编辑网格", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = gridWidth.toFloat(),
                onValueChange = { onGridWidthChange(it.roundToInt()) },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("${gridWidth} 列", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  添加元素菜单（T-08 辅助）
// ════════════════════════════════════════════════════════════

@Composable
private fun AddElementMenu(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val elementTypes = listOf(
        "DigitalCommonButton" to "普通按钮",
        "DigitalSwitchButton" to "开关按钮",
        "AnalogStick" to "摇杆",
        "DigitalPad" to "方向键",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x44000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("添加元素", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                elementTypes.forEach { (type, label) ->
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  工具函数
// ════════════════════════════════════════════════════════════

private fun placeholderView(ctx: android.content.Context, message: String): View {
    return FrameLayout(ctx).also { container ->
        container.setBackgroundColor(0x33000000)
        val tv = android.widget.TextView(ctx).apply {
            text = message
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        container.addView(tv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }
}

private fun <T> T.roundToInt(): Int where T : Number = this.toFloat().roundToInt()
