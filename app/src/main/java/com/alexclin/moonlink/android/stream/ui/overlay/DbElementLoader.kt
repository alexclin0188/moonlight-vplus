package com.alexclin.moonlink.android.stream.ui.overlay

import android.content.Context
import com.alexclin.moonlink.android.stream.ui.editor.EditorElement
import com.alexclin.moonlink.android.stream.ui.editor.ElementType
import com.alexclin.moonlink.android.stream.ui.editor.toEditorElement
import com.alexclin.moonlink.android.stream.editor.sqlite.SuperConfigDatabaseHelper
import com.limelight.preferences.PreferenceConfiguration

/**
 * 从 DB 加载按键映射元素。
 *
 * 内置方案（configId=0）直接通过 [DefaultElements] 在内存中生成，
 * 不写入数据库。用户方案直接从 DB 读取。
 */
object DbElementLoader {

    /**
     * 加载指定方案的按键映射元素。
     *
     * @param db       数据库 helper
     * @param configId 方案 ID
     * @param context  Android 上下文（用于获取屏幕尺寸和偏好）
     * @return [EditorElement] 列表
     */
    fun loadElements(
        db: SuperConfigDatabaseHelper,
        configId: Long,
        context: Context,
    ): List<EditorElement> {
        // 内置方案：在内存中生成默认元素，不写入 DB
        if (configId == 0L) {
            val config = PreferenceConfiguration.readPreferences(context)
            val dm = context.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            val isPortrait = context.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_PORTRAIT
            val defaultCvs = DefaultElements.createDefaultElements(
                w, h,
                onlyL3R3 = config.onlyL3R3,
                halfHeightPortrait = config.halfHeightOscPortrait,
                isPortrait = isPortrait
            )
            return defaultCvs.map { cv ->
                val attrsMap = HashMap<String, Any?>()
                for (key in cv.keySet()) {
                    val v = cv.get(key)
                    if (v != null) attrsMap[key] = v
                }
                attrsMap.toEditorElement()
            }
        }

        // 用户方案：从 DB 读取，过滤掉已删除的元素类型（组按键、轮盘按键）
        return db.queryAllElementIds(configId).mapNotNull { id ->
            val attrs = db.queryAllElementAttributes(configId, id)
            if (attrs.isEmpty()) null else attrs.toEditorElement()
        }.filter { it.type != ElementType.UNKNOWN }
    }
}
