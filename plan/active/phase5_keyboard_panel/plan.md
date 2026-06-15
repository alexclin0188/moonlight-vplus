# 阶段 5：键盘子面板 + 按键发送 + 直接动作（修订版）

> 本计划基于 `design/streamplan/06_阶段5_键盘面板与按键发送.md` 经审查修订。
> 审查日期：2026-06-15 | 审查结论：10 项修正已确认

---

## 目标

- 在 `StreamEngine` 中添加按键发送便捷方法（替代原计划独立 KeySender）
- 实现 `CustomKeyRepository` 存储层（完整 save/delete）
- 实现 `KeyboardSubPanel` 键盘子面板（Compose）
- 实现 `AddCustomKeyDialog` / `DeleteCustomKeyDialog` 弹窗
- 竖向窄条中两个直接动作按钮（显示桌面/展示所有窗口）生效
- 键盘子面板集成到 StreamOverlay（含动画）

## 预计工时

2 天

---

## 5.1 StreamEngine 按键便捷方法

**位置**：`app/src/main/java/com/alexclin/moonlink/stream/engine/StreamEngine.kt`

### 变更 1：统一 KEY_UP_DELAY 为 25ms

现有 `sendKeys()` 使用 50ms 延迟 → 改为 **25ms**，与 GameMenu 一致。

```kotlin
// handler.postDelayed({ ... }, 50)  →  handler.postDelayed({ ... }, 25)
```

### 变更 2：postDelayed 回调中加 null 检查

```kotlin
handler.postDelayed({
    val c = conn ?: return@postDelayed  // ← 新增 null 检查
    var mod = finalModifier
    for (pos in keys.indices.reversed()) {
        val key = keys[pos]
        mod = (mod.toInt() and getKeyModifier(key).toInt().inv()).toByte()
        c.sendKeyboardInput(key, KeyboardPacket.KEY_UP, mod, 0.toByte())
    }
}, KEY_UP_DELAY)
```

### 变更 3：新增便捷方法

```kotlin
// 在 StreamEngine 中添加以下方法：

// 显示桌面 Win+D
fun sendWinD() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LWIN.toShort(),
        0x44.toShort()
    ))
}

// 展示窗口 Win+Tab
fun sendWinTab() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LWIN.toShort(),
        KeyboardTranslator.VK_TAB.toShort()
    ))
}

// 锁定 Win+L
fun sendWinL() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LWIN.toShort(),
        0x4C.toShort()
    ))
}

// 主机键盘 Win+Ctrl+O
fun sendWinCtrlO() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LWIN.toShort(),
        KeyboardTranslator.VK_LCONTROL.toShort(),
        0x4F.toShort()
    ))
}

// 任务管理器 Ctrl+Shift+Esc
fun sendCtrlShiftEsc() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LCONTROL.toShort(),
        KeyboardTranslator.VK_LSHIFT.toShort(),
        KeyboardTranslator.VK_ESCAPE.toShort()
    ))
}

// Win 键
fun sendWin() {
    sendKeys(shortArrayOf(KeyboardTranslator.VK_LWIN.toShort()))
}

// Alt+Tab
fun sendAltTab() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_MENU.toShort(),
        KeyboardTranslator.VK_TAB.toShort()
    ))
}

// Alt+F4
fun sendAltF4() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_MENU.toShort(),
        (KeyboardTranslator.VK_F1 + 3).toShort()
    ))
}

// 睡眠 Win+X → U+S（两段式，延迟 200ms）
fun sendSleep() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LWIN.toShort(),
        0x58.toShort()  // 'X'
    ))
    handler.postDelayed({
        sendKeys(shortArrayOf(
            0x55.toShort(),  // 'U'
            0x53.toShort()   // 'S'
        ))
    }, 200L)
}

// HDR 切换 Win+Alt+B
fun sendHdrToggle() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LWIN.toShort(),
        KeyboardTranslator.VK_MENU.toShort(),
        0x42.toShort()
    ))
}

// 远程鼠标 Ctrl+Alt+Shift+N（与旧 GameMenu 一致，使用 VK_N = 78）
fun sendRemoteMouseToggle() {
    sendKeys(shortArrayOf(
        KeyboardTranslator.VK_LCONTROL.toShort(),
        KeyboardTranslator.VK_MENU.toShort(),
        KeyboardTranslator.VK_LSHIFT.toShort(),
        78.toShort()  // VK_N
    ))
}
```

> **不创建独立 KeySender.kt**。所有按键发送统一由 `StreamEngine` 提供，避免与已有的 `StreamEngine.sendKeys()` 重复。

---

## 5.2 CustomKeyRepository.kt

**路径**：`app/src/main/java/com/alexclin/moonlink/stream/ui/common/CustomKeyRepository.kt`

### 变更

- `CustomKeyData.keys` 类型从 `ShortArray` 改为 **`List<Short>`**（避免 data class equals/hashCode 引用比较问题）
- `save()` 和 `delete()` 补充完整实现
- 输入验证：非空检查、hex 格式检查（`0x` 前缀）、重复名称检查

```kotlin
object CustomKeyRepository {
    private const val PREF_NAME = "custom_special_keys"
    private const val KEY_NAME = "data"

    data class CustomKeyData(val name: String, val keys: List<Short>)

    /** 加载所有自定义按键 */
    fun loadAll(context: Context): List<CustomKeyData> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_NAME, null) ?: return emptyList()

        return try {
            val root = JSONObject(json)
            val array = root.getJSONArray("data")
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val codes = obj.getJSONArray("data")
                val keys = (0 until codes.length()).map { j ->
                    val hex = codes.getString(j)
                    if (hex.startsWith("0x")) hex.substring(2).toInt(16).toShort()
                    else 0 // 格式异常跳过
                }
                CustomKeyData(name, keys)
            }
        } catch (e: Exception) {
            // JSON 解析失败 → 返回空列表
            emptyList()
        }
    }

    /** 保存自定义按键 */
    fun save(context: Context, name: String, keyCodesHex: List<String>) {
        // 输入验证
        if (name.isBlank() || keyCodesHex.isEmpty()) return

        // 验证每个键码格式
        for (hex in keyCodesHex) {
            if (!hex.startsWith("0x") || hex.length < 3) {
                return  // 格式无效
            }
        }

        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = preferences.getString(KEY_NAME, "{\"data\":[]}") ?: "{\"data\":[]}"

        try {
            val root = JSONObject(value)
            val dataArray = root.getJSONArray("data")

            // 检查重复名称
            for (i in 0 until dataArray.length()) {
                val existingName = dataArray.getJSONObject(i).optString("name")
                if (existingName == name) {
                    return  // 名称已存在
                }
            }

            val keyCodesArray = JSONArray()
            for (hex in keyCodesHex) {
                keyCodesArray.put(hex)
            }

            val newKeyEntry = JSONObject()
            newKeyEntry.put("name", name)
            newKeyEntry.put("data", keyCodesArray)
            dataArray.put(newKeyEntry)

            preferences.edit { putString(KEY_NAME, root.toString()) }
        } catch (e: Exception) {
            // 保存失败静默处理
        }
    }

    /** 删除指定按键（按名称匹配） */
    fun delete(context: Context, name: String) {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = preferences.getString(KEY_NAME, null) ?: return

        try {
            val root = JSONObject(value)
            val dataArray = root.getJSONArray("data")
            val newArray = JSONArray()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                if (obj.optString("name") != name) {
                    newArray.put(obj)
                }
            }

            if (newArray.length() < dataArray.length()) {
                root.put("data", newArray)
                preferences.edit { putString(KEY_NAME, root.toString()) }
            }
        } catch (e: Exception) {
            // 删除失败静默处理
        }
    }

    /** 批量删除 */
    fun deleteAll(context: Context, names: List<String>) {
        for (name in names) {
            delete(context, name)
        }
    }
}
```

### 向后兼容

- 与旧 GameMenu 使用**相同的 SharedPreferences key**（`custom_special_keys` / `data`）
- JSON 格式完全一致：`{"data": [{"name": "...", "data": ["0x...", ...]}]}`
- 新代码保存的数据可被旧 GameMenu 读取，反之亦然

---

## 5.3 KeyboardSubPanel.kt

**路径**：`app/src/main/java/com/alexclin/moonlink/stream/ui/keyboard/KeyboardSubPanel.kt`

### 布局（不变）

```
┌──────────────────────────┐
│ ← 返回    键盘            │  ← 标题栏
├──────────────────────────┤
│                          │
│  [屏幕键盘]    切换开关     │  ← 屏幕键盘
│  [主机键盘]    发送按钮     │  ← 主机键盘（Win+Ctrl+O）
│                          │
│ ── 快捷按键 ──            │
│ ┌──────────────────────┐ │
│ │ Ctrl+C               │ │  ← 自定义按键列表
│ │ Ctrl+V               │ │     （LazyColumn）
│ │ 我的组合键1            │ │
│ └──────────────────────┘ │
│                          │
│  [+ 添加自定义按键]       │  ← 添加按钮
│  [删除自定义按键]         │  ← 删除按钮
└──────────────────────────┘
```

### 实现要点

- 调用 `engine.sendWinCtrlO()` 替代 `KeySender.sendWinCtrlO(engine.conn)`
- 调用 `engine.sendKeys(key.keys.toShortArray())` 替代 `KeySender.sendKeys(engine.conn, key.keys)`
- 不需要直接访问 `engine.conn`

```kotlin
@Composable
fun KeyboardSubPanel(engine: StreamEngine) {
    val context = LocalContext.current
    var customKeys by remember { mutableStateOf(CustomKeyRepository.loadAll(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // ── 标题栏 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* 由 StreamOverlay 控制关闭 */ }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text("键盘", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ── 屏幕键盘 ──
        KeyboardActionButton(
            icon = Icons.Default.Keyboard,
            label = "屏幕键盘",
            onClick = { engine.toggleKeyboard() }
        )

        // ── 主机键盘 ──
        KeyboardActionButton(
            icon = Icons.Default.Computer,
            label = "主机键盘",
            onClick = { engine.sendWinCtrlO() }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // ── 快捷按键列表 ──
        Text("快捷按键",
             style = MaterialTheme.typography.titleSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (customKeys.isEmpty()) {
            Text("暂无自定义按键",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                 modifier = Modifier.padding(vertical = 8.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                items(customKeys) { key ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { engine.sendKeys(key.keys.toShortArray()) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(key.name,
                             modifier = Modifier.padding(12.dp, 10.dp),
                             style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // ── 添加按钮 ──
        var showAddDialog by remember { mutableStateOf(false) }
        TextButton(onClick = { showAddDialog = true },
                   modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加自定义按键")
        }

        // ── 删除按钮 ──
        if (customKeys.isNotEmpty()) {
            var showDeleteDialog by remember { mutableStateOf(false) }
            TextButton(onClick = { showDeleteDialog = true },
                       modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除自定义按键")
            }
        }
    }

    // 添加弹窗
    if (showAddDialog) {
        AddCustomKeyDialog(
            onSave = { name, codes ->
                CustomKeyRepository.save(context, name, codes)
                customKeys = CustomKeyRepository.loadAll(context)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // 删除弹窗
    if (showDeleteDialog) {
        DeleteCustomKeyDialog(
            keys = customKeys,
            onDelete = { names ->
                CustomKeyRepository.deleteAll(context, names)
                customKeys = CustomKeyRepository.loadAll(context)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
```

### KeyboardActionButton（复用）

```kotlin
@Composable
private fun KeyboardActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                 tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium,
                 modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}
```

---

## 5.4 AddCustomKeyDialog.kt

**路径**：`app/src/main/java/com/alexclin/moonlink/stream/ui/keyboard/AddCustomKeyDialog.kt`

### 交互设计

- **名称**：`OutlinedTextField`，单行输入
- **键码**：`OutlinedTextField`，接受格式如 `0x5B,0x44`（十六进制，逗号分隔）
- **保存按钮**：验证名称非空 + 键码格式正确 → 调用 `CustomKeyRepository.save()`
- **取消按钮**：关闭弹窗
- 输入验证错误时显示 Toast 提示

```kotlin
@Composable
fun AddCustomKeyDialog(
    onSave: (name: String, codes: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var codesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义按键") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = codesText,
                    onValueChange = { codesText = it },
                    label = { Text("键码（十六进制，逗号分隔）") },
                    placeholder = { Text("例如: 0x5B, 0x44") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedName = name.trim()
                val codes = codesText.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                when {
                    trimmedName.isEmpty() -> { /* Toast: 名称不能为空 */ }
                    codes.isEmpty() -> { /* Toast: 键码不能为空 */ }
                    codes.any { !it.startsWith("0x") || it.length < 3 } -> { /* Toast: 键码格式错误 */ }
                    else -> {
                        onSave(trimmedName, codes)
                    }
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
```

---

## 5.5 DeleteCustomKeyDialog.kt

**路径**：`app/src/main/java/com/alexclin/moonlink/stream/ui/keyboard/DeleteCustomKeyDialog.kt`

### 交互设计

- 多选列表（每项带 Checkbox），列出所有自定义按键名称
- "删除"按钮：删除选中项
- "取消"按钮：关闭弹窗
- 未选择任何项时禁用删除按钮

```kotlin
@Composable
fun DeleteCustomKeyDialog(
    keys: List<CustomKeyData>,
    onDelete: (names: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val checked = remember { mutableStateListOf(*keys.map { false }.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要删除的按键") },
        text = {
            LazyColumn {
                itemsIndexed(keys) { index, key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked[index] = !checked[index] }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(key.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = keys.filterIndexed { i, _ -> checked[i] }.map { it.name }
                    if (selected.isNotEmpty()) onDelete(selected)
                },
                enabled = checked.any { it }
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
```

---

## 5.6 StreamOverlay 集成

**位置**：`app/src/main/java/com/alexclin/moonlink/stream/ui/StreamOverlay.kt`

### 集成 1：直接动作（替换 TODO）

```kotlin
"show_desktop" -> {
    engine.sendWinD()
    panelState = PanelState.HIDDEN
    activeEntry = null
    autoHideDone = true
}
"show_windows" -> {
    engine.sendWinTab()
    panelState = PanelState.HIDDEN
    activeEntry = null
    autoHideDone = true
}
```

### 集成 2：键盘子面板 + 动画

```kotlin
// ── 横竖屏判断 ──
val isLandscape = LocalConfiguration.current.orientation ==
    android.content.res.Configuration.ORIENTATION_LANDSCAPE

// ── 键盘子面板（横屏靠右滑入，竖屏底部弹出） ──
val keyboardEnter = if (isLandscape) {
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(200))
} else {
    slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(200))
}
val keyboardExit = if (isLandscape) {
    slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(150))
} else {
    slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(150))
}

AnimatedVisibility(
    visible = panelState == PanelState.KEYBOARD_PANEL,
    enter = keyboardEnter,
    exit = keyboardExit,
    modifier = Modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter),
) {
    Surface(
        modifier = Modifier
            .then(
                if (isLandscape) Modifier.width(300.dp).fillMaxHeight()
                else Modifier.fillMaxWidth()
            ),
        shape = if (isLandscape) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        KeyboardSubPanel(engine = engine)
    }
}
```

---

## 5.7 任务清单与批次

### 批次 1：StreamEngine 便捷方法（3 项）
1. 修改 `StreamEngine.sendKeys()` 延迟 50ms → 25ms，加 null 检查
2. 添加 11 个快捷方法（sendWinD, sendWinTab, sendWinL, sendWinCtrlO, sendCtrlShiftEsc, sendWin, sendAltTab, sendAltF4, sendSleep, sendHdrToggle, sendRemoteMouseToggle）
3. 验证 QuickActionRow 不受影响（回归测试）

### 批次 2：CustomKeyRepository + 弹窗（3 项）
4. 实现 `CustomKeyRepository.kt`（完整 save/delete/loadAll，验证逻辑）
5. 实现 `AddCustomKeyDialog.kt`（Compose AlertDialog）
6. 实现 `DeleteCustomKeyDialog.kt`（Compose AlertDialog + 多选列表）

### 批次 3：KeyboardSubPanel + StreamOverlay 集成（3 项）
7. 实现 `KeyboardSubPanel.kt`（Compose 面板 + 自定义按键列表 + 弹窗调用）
8. 集成到 StreamOverlay（替换 TODO，添加条件动画）
9. 实现横竖屏适配（横屏靠右滑入，竖屏底部全宽弹出）

### 批次 4：验证（2 项）
10. 手动验证 8 项 + 回归测试
11. 向后兼容验证（新旧代码读/写同一 SharedPreferences）

---

## 5.8 产出验证（更新版）

1. 竖向窄条点击"键盘" → 键盘子面板出现（动画正确）
2. 屏幕键盘按钮可切换 Android 软键盘
3. 主机键盘按钮发送 Win+Ctrl+O
4. 自定义按键列表正常显示
5. 添加自定义按键弹窗可输入名称和十六进制键码
6. 删除按键弹窗可勾选并删除多项
7. 点击"显示桌面" → 面板关闭，Win+D 发送到主机
8. 点击"展示所有窗口" → 面板关闭，Win+Tab 发送到主机
9. **[新增] 回归测试**：QuickActionRow 的快捷按键（Win、HDR、睡眠等）仍然正常工作
10. **[新增] 向后兼容**：用新代码保存的自定义按键，可在旧 GameMenu 中读取；旧 GameMenu 保存的数据也可被新 KeyboardSubPanel 读取
11. **[新增] 横竖屏**：横屏时键盘面板从右侧滑入，竖屏时从底部弹出

---

## 文件变更清单

| 操作 | 路径 | 说明 |
|------|------|------|
| 修改 | `stream/engine/StreamEngine.kt` | sendKeys 延迟 50→25ms，加 null 检查，新增 11 个便捷方法 |
| 新建 | `stream/ui/common/CustomKeyRepository.kt` | 自定义按键 SharedPreferences 存储 |
| 新建 | `stream/ui/keyboard/KeyboardSubPanel.kt` | 键盘子面板 Composable |
| 新建 | `stream/ui/keyboard/AddCustomKeyDialog.kt` | 添加自定义按键弹窗 |
| 新建 | `stream/ui/keyboard/DeleteCustomKeyDialog.kt` | 删除自定义按键弹窗 |
| 修改 | `stream/ui/StreamOverlay.kt` | 替换 TODO，集成键盘面板+动画 |

> **不创建** `stream/ui/common/KeySender.kt`（原计划废弃）

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| sendKeys 延迟由 50ms 改为 25ms 导致部分主机按键响应异常 | 回归验证 QuickActionRow 所有快捷按键；保留降级为 50ms 的选项 |
| CustomKeyRepository JSON 格式与旧版不兼容 | 完全复用旧 GameMenu 的 SharedPreferences key 和 JSON 结构，不做任何格式变更 |
| 横竖屏动画切换导致布局闪烁 | 使用 Compose `AnimatedVisibility` 的标准动画，避免手动条件切换 |
| Handler postDelayed 在 Activity 销毁后执行 | 回调中检查 conn != null |
