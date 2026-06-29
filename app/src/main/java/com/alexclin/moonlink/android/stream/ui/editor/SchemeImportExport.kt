package com.alexclin.moonlink.android.stream.ui.editor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 按键方案导入导出工具。
 *
 * 使用 [SuperConfigDatabaseHelper] 已有的 export/import API，
 * 通过 Android ContentResolver 读写外部文件。
 *
 * JSON 格式（与旧版 CROWN 兼容）：
 * ```json
 * {
 *   "version": 9,
 *   "settings": "{...config ContentValues JSON...}",
 *   "elements": "[{...element ContentValues JSON...}, ...]",
 *   "md5": "checksum"
 * }
 * ```
 */

object SchemeExporter {

    /** 导出文件的 MIME 类型 */
    const val MIME_TYPE = "application/octet-stream"

    /** 导出的默认文件名 */
    const val DEFAULT_FILENAME = "moonlink_scheme.mlk"

    /**
     * 将当前方案导出到指定 URI。
     *
     * @return true 表示导出成功
     */
    fun export(context: Context, db: SuperConfigDatabaseHelper, configId: Long, uri: Uri): Boolean {
        return try {
            val json = db.exportConfig(configId)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            ToastUtil.show(context, "导出失败: ${e.message}", Toast.LENGTH_LONG)
            false
        }
    }

    /**
     * 从指定 URI 导入方案，并自动切换到新方案。
     *
     * @param onImported 导入成功后的回调，参数为新的 configId
     */
    fun import(
        context: Context,
        db: SuperConfigDatabaseHelper,
        uri: Uri,
        onImported: (newConfigId: Long) -> Unit,
    ) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: run {
                ToastUtil.show(context, "无法读取文件", Toast.LENGTH_LONG)
                return
            }

            val result = db.importConfig(json)
            when (result) {
                0 -> {
                    // 成功导入 — 查询所有 configId，取 id 最大的（新导入的）
                    val allConfigIds = db.queryAllConfigIds()
                    val newConfigId = if (allConfigIds.isNotEmpty()) {
                        allConfigIds.max()
                    } else {
                        ToastUtil.show(context, "导入成功但未找到方案", Toast.LENGTH_LONG)
                        return
                    }
                    ToastUtil.show(context, "导入成功", Toast.LENGTH_SHORT)
                    onImported(newConfigId)
                }
                -1 -> ToastUtil.show(context, "文件格式错误", Toast.LENGTH_LONG)
                -2 -> ToastUtil.show(context, "文件校验失败（已损坏）", Toast.LENGTH_LONG)
                -3 -> ToastUtil.show(context, "版本不兼容，无法导入", Toast.LENGTH_LONG)
                else -> ToastUtil.show(context, "导入失败 (错误: $result)", Toast.LENGTH_LONG)
            }
        } catch (e: Exception) {
            ToastUtil.show(context, "导入失败: ${e.message}", Toast.LENGTH_LONG)
        }
    }
}
