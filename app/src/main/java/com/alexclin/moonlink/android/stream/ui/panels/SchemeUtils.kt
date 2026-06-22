package com.alexclin.moonlink.android.stream.ui.panels

import android.content.Context
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper

/**
 * 按键方案数据模型（共享版本，替换各文件中重复的 SchemeInfo / ConfigScheme）。
 */
data class SchemeInfo(
    val configId: Long,
    val name: String,
    val isDefault: Boolean = configId == 0L,
)

/**
 * 从数据库加载所有用户方案（跳过 configId=0 的内置方案）。
 */
fun loadUserSchemes(context: Context): List<SchemeInfo> {
    return try {
        val db = SuperConfigDatabaseHelper(context)
        val ids = db.queryAllConfigIds()
        ids.mapNotNull { id ->
            if (id == 0L) return@mapNotNull null
            val name = db.queryConfigAttribute(
                id, PageConfigController.COLUMN_STRING_CONFIG_NAME, "未命名"
            ) as? String ?: "未命名"
            SchemeInfo(configId = id, name = name)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 查询所有用户方案的名称列表（用于重名校验）。
 */
fun loadAllSchemeNames(context: Context): List<String> {
    return try {
        val db = SuperConfigDatabaseHelper(context)
        db.queryAllConfigIds().mapNotNull { id ->
            if (id == 0L) return@mapNotNull null
            db.queryConfigAttribute(id, PageConfigController.COLUMN_STRING_CONFIG_NAME, null) as? String
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 校验方案名称是否在数据库中已存在（不区分大小写）。
 * @param context Android 上下文
 * @param name 要校验的名称
 * @return true 表示已存在同名方案
 */
fun isSchemeNameDuplicate(context: Context, name: String): Boolean {
    return loadAllSchemeNames(context).any { it.equals(name, ignoreCase = true) }
}

/**
 * 将配置面板的设置值同步到 [StreamEngine] 的运行时状态，
 * 使 [KeyMappingOverlay] 立即响应（展示和触控行为）。
 *
 * 新架构替代 [syncToControllers] 的方案，不依赖旧的 ControllerManager。
 */
fun syncConfigToEngine(
    engine: StreamEngine,
    touchEnabled: Boolean,
    gameVibrator: Boolean,
    buttonVibrator: Boolean,
    wheelSpeed: Int,
    enhancedTouch: Boolean,
    globalOpacity: Int,
) {
    engine.configTouchEnabled = touchEnabled
    engine.configGameVibrator = gameVibrator
    engine.configButtonVibrator = buttonVibrator
    engine.configWheelSpeed = wheelSpeed
    engine.configEnhancedTouch = enhancedTouch
    engine.configGlobalOpacity = globalOpacity
}

