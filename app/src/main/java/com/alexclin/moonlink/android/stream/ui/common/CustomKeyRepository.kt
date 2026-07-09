package com.alexclin.moonlink.android.stream.ui.common

import android.content.Context
import com.alexclin.moonlink.android.stream.ui.keyboard.ShortcutDefinitions
import com.alexclin.moonlink.android.util.LimeLog
import org.json.JSONArray
import org.json.JSONObject
import com.alexclin.moonlink.android.R

/**
 * 自定义快捷键数据模型。
 *
 * 与旧 GameMenu 的 [com.limelight.GameMenu] 内部类 [CustomKeyData] 格式兼容，
 * 但 [keys] 类型使用 [List]<[Short]> 而非 [ShortArray]。
 *
 * @param name 快捷键显示名（如 "Ctrl+C"）
 * @param description 功能说明（如 "复制"）
 * @param keys 键值数组（Windows VK 码）
 */
data class CustomKeyData(
    val name: String,
    val description: String = "",
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

    /** 初始化标记 key */
    private const val KEY_INITIALIZED = "initialized"

    // ── 公开 API ──

    /**
     * 从 SharedPreferences 加载所有快捷键。
     *
     * 如果 SP 中尚无数据且尚未初始化，会自动执行 [initializeDefaults] 初始化内置快捷键，
     * 然后重新读取。
     */
    fun loadAll(context: Context): List<CustomKeyData> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)

        // 首次使用且未初始化：写入内置快捷键并标记
        if (!isInitialized) {
            initializeDefaults(context)
        }

        val jsonStr = prefs.getString(KEY_NAME, null) ?: return emptyList()
        return parseJson(jsonStr)
    }

    /**
     * 将内置快捷键定义初始化写入 SharedPreferences 并标记已初始化。
     *
     * 仅在 [KEY_INITIALIZED] 为 false 或强制执行时调用。
     * 不会覆盖已有数据，仅当 SP 中无数据时写入。
     *
     * @param context Context
     * @param force 是否强制重新写入（用于重置）
     */
    fun initializeDefaults(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (!force) {
            val existingData = prefs.getString(KEY_NAME, null)
            if (!existingData.isNullOrEmpty()) {
                // 有数据且不强制写入，只标记已初始化
                prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
                return
            }
        }

        // 将内置快捷键转为 JSON 写入（包含多语言 description）
        val jsonStr = buildDefaultsJson(context)
        if (jsonStr != null) {
            prefs.edit()
                .putString(KEY_NAME, jsonStr)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        } else {
            // 即使没有内置快捷键也标记已初始化，避免每次都尝试
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        }
    }

    /**
     * 重置所有快捷键配置：
     * 1. 清除 SP 中的所有数据（快捷键列表和初始化标记）
     * 2. 重新调用 [initializeDefaults] 写入内置快捷键
     */
    fun resetAll(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_NAME)
            .remove(KEY_INITIALIZED)
            .apply()

        // 强制重新初始化
        initializeDefaults(context, force = true)
    }

    /**
     * 检查是否已完成初始化。
     */
    fun isInitialized(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INITIALIZED, false)
    }

    /**
     * 保存一个新快捷键。
     *
     * @param context Context
     * @param name 快捷键名称（不能为空）
     * @param description 功能说明
     * @param hexCodes 十六进制键码列表，每项须以 "0x" 开头（如 "0x7A"）
     * @return [SaveResult] 表示保存成功或失败原因
     */
    fun save(context: Context, name: String, description: String = "", hexCodes: List<String>): SaveResult {
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

        // 确保已初始化（SP 中有数据）
        if (!isInitialized(context)) {
            initializeDefaults(context)
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
                val hexDigits = code.substring(2)
                codesArray.put("0x$hexDigits")
            }

            val newEntry = JSONObject()
            newEntry.put("name", trimmedName)
            if (description.isNotEmpty()) {
                newEntry.put("description", description)
            }
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
     * 删除指定名称的快捷键。
     *
     * @param context Context
     * @param namesToDelete 要删除的快捷键名称列表
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

    /**
     * 从 [ShortcutDefinitions.builtinCustomKeys] 生成 JSON 字符串。
     * description 从字符串资源中加载以实现多语言。
     */
    private fun buildDefaultsJson(context: Context): String? {
        val builtins = ShortcutDefinitions.builtinCustomKeys
        if (builtins.isEmpty()) return null

        try {
            val dataArray = JSONArray()
            for (builtin in builtins) {
                val codesArray = JSONArray()
                for (key in builtin.keys) {
                    val hexStr = "0x${key.toInt().and(0xFF).toString(16).uppercase()}"
                    codesArray.put(hexStr)
                }
                val entry = JSONObject()
                entry.put("name", builtin.name)
                // 从字符串资源加载 description 以支持多语言
                val description = try {
                    context.getString(builtin.descriptionResId)
                } catch (_: Exception) {
                    null
                }
                if (!description.isNullOrEmpty()) {
                    entry.put("description", description)
                }
                entry.put("data", codesArray)
                dataArray.put(entry)
            }

            val root = JSONObject()
            root.put(KEY_NAME, dataArray)
            return root.toString()
        } catch (e: Exception) {
            LimeLog.warning("CustomKeyRepository: failed to build defaults JSON: ${e.message}")
            return null
        }
    }

    /**
     * 解析 JSON 字符串为 [CustomKeyData] 列表。
     */
    private fun parseJson(jsonStr: String): List<CustomKeyData> {
        val result = mutableListOf<CustomKeyData>()
        try {
            val root = JSONObject(jsonStr)
            val dataArray = root.optJSONArray(KEY_NAME) ?: return result
            for (i in 0 until dataArray.length()) {
                val entry = dataArray.getJSONObject(i)
                val name = entry.optString("name", "")
                val description = entry.optString("description", "")
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
                    result.add(CustomKeyData(name, description, keys))
                }
            }
        } catch (e: Exception) {
            LimeLog.warning("CustomKeyRepository: failed to parse JSON: ${e.message}")
        }
        return result
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
