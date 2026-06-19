package com.alexclin.moonlink.stream.ui.overlay

import android.content.Context
import com.alexclin.moonlink.stream.ui.editor.EditorElement
import com.alexclin.moonlink.stream.ui.editor.toEditorElement
import com.limelight.binding.input.advance_setting.element.Element
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import com.limelight.preferences.PreferenceConfiguration

/**
 * 从 DB 加载按键映射元素。
 *
 * 内置方案（configId=0）每次加载时都通过 [DefaultElements] 重新生成，
 * 确保布局始终是最新版本。用户方案直接从 DB 读取。
 */
object DbElementLoader {

    /**
     * 从 DB 加载指定方案的按键映射元素。
     *
     * @param db       数据库 helper
     * @param configId 方案 ID
     * @param context  Android 上下文（首次填充内置方案时用于获取屏幕尺寸和偏好）
     * @return [EditorElement] 列表
     */
    fun loadElements(
        db: SuperConfigDatabaseHelper,
        configId: Long,
        context: Context,
    ): List<EditorElement> {
        // 内置方案：始终重新生成，确保使用最新布局
        if (configId == 0L) {
            // 清除旧元素（来自旧版布局）
            for (id in db.queryAllElementIds(configId)) {
                db.deleteElement(configId, id)
            }

            val config = PreferenceConfiguration.readPreferences(context)
            val dm = context.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            val isPortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val defaultCvs = DefaultElements.createDefaultElements(w, h, config, isPortrait)
            for (cv in defaultCvs) {
                cv.put(Element.COLUMN_LONG_CONFIG_ID, 0L)
                db.insertElement(cv)
            }
            // 写入 config 表的 scheme_type 属性
            val configCv = android.content.ContentValues()
            configCv.put("scheme_type", "virtual_controller")
            db.updateConfig(0L, configCv)

            val ids = db.queryAllElementIds(configId)
            return ids.mapNotNull { id ->
                val attrs = db.queryAllElementAttributes(configId, id)
                if (attrs.isEmpty()) null else attrs.toEditorElement()
            }
        }

        // 用户方案：从 DB 读取
        return db.queryAllElementIds(configId).mapNotNull { id ->
            val attrs = db.queryAllElementAttributes(configId, id)
            if (attrs.isEmpty()) null else attrs.toEditorElement()
        }
    }
}
