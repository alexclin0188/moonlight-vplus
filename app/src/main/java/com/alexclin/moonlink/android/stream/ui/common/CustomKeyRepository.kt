package com.alexclin.moonlink.android.stream.ui.common

import android.content.Context
import com.alexclin.moonlink.android.util.LimeLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.alexclin.moonlink.android.R

/**
 * 自定义按键数据模型。
 *
 * 与旧 GameMenu 的 [com.limelight.GameMenu] 内部类 [CustomKeyData] 格式兼容，
 * 但 [keys] 类型使用 [List]<[Short]> 而非 [ShortArray]。
 */
data class CustomKeyData(
    val name: String,
    val keys: List<Short>,
)

/**
 * 自定义按键 SharedPreferences 仓库。
 *
 * 读写与旧 GameMenu 同一份 SharedPreferences（[PREF_NAME]/[KEY_NAME]），
 * JSON 结构完全兼容，实现双向读写。
 *
 * 所有方法均为伴生对象方法，直接接收 [Context] 即可调用。
 */
object CustomKeyRepository {

    /** SharedPreferences 文件名（与 GameMenu 共用） */
    private const val PREF_NAME = "custom_special_keys"

    /** SharedPreferences 内的 key 名（与 GameMenu 共用） */
    private const val KEY_NAME = "data"

    private const val DEFAULT_RAW_RESOURCE = com.alexclin.moonlink.android.R.raw.default_special_keys

    // ── 公开 API ──

    /**
     * 从 SharedPreferences 加载所有自定义按键。
     * 若 SharedPreferences 为空，则从默认 raw 资源文件加载并持久化。
     */
    fun loadAll(context: Context): List<CustomKeyData> {
        val result = mutableListOf<CustomKeyData>()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var jsonStr = prefs.getString(KEY_NAME, null)

        // 首次使用：从默认 raw 资源加载
        if (jsonStr.isNullOrEmpty()) {
            jsonStr = readRawResource(context, DEFAULT_RAW_RESOURCE)
            if (!jsonStr.isNullOrEmpty()) {
                prefs.edit().putString(KEY_NAME, jsonStr).apply()
            }
        }

        if (jsonStr.isNullOrEmpty()) return result

        try {
            val root = JSONObject(jsonStr)
            val dataArray = root.optJSONArray("data") ?: return result
            for (i in 0 until dataArray.length()) {
                val entry = dataArray.getJSONObject(i)
                val name = entry.optString("name", "")
                val codesArray = entry.optJSONArray("data") ?: continue
                val keys = mutableListOf<Short>()
                for (j in 0 until codesArray.length()) {
                    val hexStr = codesArray.optString(j, "")
                    if (hexStr.startsWith("0x") || hexStr.startsWith("0X")) {
                        try {
                            keys.add(hexStr.substring(2).toInt(16).toShort())
                        } catch (_: NumberFormatException) {
                            LimeLog.warning("CustomKeyRepository: invalid hex code: $hexStr")
                        }
                    }
                }
                if (name.isNotEmpty()) {
                    result.add(CustomKeyData(name, keys))
                }
            }
        } catch (e: Exception) {
            LimeLog.warning("CustomKeyRepository: failed to load keys: ${e.message}")
        }

        return result
    }

    /**
     * 保存一个新自定义按键。
     *
     * @param context Context
     * @param name 按键名称（不能为空）
     * @param hexCodes 十六进制键码列表，每项须以 "0x" 开头（如 "0x7A"）
     * @return [SaveResult] 表示保存成功或失败原因
     */
    fun save(context: Context, name: String, hexCodes: List<String>): SaveResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return SaveResult.Error(context.getString(R.string.custom_key_error_name_empty))
        }

        // 验证 hexCodes 格式
        for (code in hexCodes) {
            if (!code.startsWith("0x") && !code.startsWith("0X")) {
                return SaveResult.Error(context.getString(R.string.custom_key_error_format, code))
            }
            try {
                code.substring(2).toInt(16)
            } catch (_: NumberFormatException) {
                return SaveResult.Error(context.getString(R.string.custom_key_error_invalid_hex, code))
            }
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_NAME, "{\"$KEY_NAME\":[]}") ?: "{\"$KEY_NAME\":[]}"

        try {
            val root = JSONObject(jsonStr)
            val dataArray = root.getJSONArray(KEY_NAME)

            // 重复名称检查
            for (i in 0 until dataArray.length()) {
                val existing = dataArray.getJSONObject(i).optString("name", "")
                if (existing == trimmedName) {
                    return SaveResult.DuplicateName(trimmedName)
                }
            }

            // 构建新条目（统一使用小写 "0x" 前缀，以兼容旧 GameMenu 读写）
            val codesArray = JSONArray()
            for (code in hexCodes) {
                // 去掉 "0x"/"0X" 前缀后保留原始十六进制数字，重新拼接小写前缀
                val hexDigits = code.substring(2)
                codesArray.put("0x$hexDigits")
            }

            val newEntry = JSONObject()
            newEntry.put("name", trimmedName)
            newEntry.put("data", codesArray)
            dataArray.put(newEntry)

            prefs.edit().putString(KEY_NAME, root.toString()).apply()
            return SaveResult.Success
        } catch (e: Exception) {
            LimeLog.warning("CustomKeyRepository: save failed: ${e.message}")
            return SaveResult.Error(context.getString(R.string.custom_key_error_save_failed, e.message ?: ""))
        }
    }

    /**
     * 删除指定名称的自定义按键。
     *
     * @param context Context
     * @param namesToDelete 要删除的按键名称列表
     * @return true 表示至少删除了一个条目
     */
    fun delete(context: Context, namesToDelete: List<String>): Boolean {
        if (namesToDelete.isEmpty()) return false

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_NAME, null) ?: return false

        try {
            val root = JSONObject(jsonStr)
            val dataArray = root.getJSONArray(KEY_NAME)
            val namesSet = namesToDelete.toSet()

            var removed = false
            for (i in dataArray.length() - 1 downTo 0) {
                val name = dataArray.getJSONObject(i).optString("name", "")
                if (name in namesSet) {
                    dataArray.remove(i)
                    removed = true
                }
            }

            if (removed) {
                root.put(KEY_NAME, dataArray)
                prefs.edit().putString(KEY_NAME, root.toString()).apply()
            }
            return removed
        } catch (e: Exception) {
            LimeLog.warning("CustomKeyRepository: delete failed: ${e.message}")
            return false
        }
    }

    /**
     * 检查指定名称是否已存在。
     */
    fun hasDuplicateName(context: Context, name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false
        return loadAll(context).any { it.name == trimmedName }
    }

    // ── 内部工具方法 ──

    private fun readRawResource(context: Context, resId: Int): String? {
        return try {
            context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            LimeLog.warning("CustomKeyRepository: failed to read raw resource $resId: $e")
            null
        }
    }
}

/**
 * 保存操作结果。
 */
sealed class SaveResult {
    /** 保存成功 */
    data object Success : SaveResult()

    /** 名称重复 */
    data class DuplicateName(val name: String) : SaveResult()

    /** 其他错误 */
    data class Error(val message: String) : SaveResult()
}
