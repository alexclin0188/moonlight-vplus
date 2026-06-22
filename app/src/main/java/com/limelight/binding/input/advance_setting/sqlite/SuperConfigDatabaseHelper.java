package com.limelight.binding.input.advance_setting.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.limelight.binding.input.advance_setting.config.PageConfigController;
import com.limelight.binding.input.advance_setting.element.Element;
import com.limelight.utils.MathUtils;

import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuperConfigDatabaseHelper extends SQLiteOpenHelper {
    private final Context context;

    private class ExportFile {
        private int version;
        private String settings;
        private String elements;
        private String md5;
        // 导出时的源设备屏幕像素尺寸，用于导入时坐标缩放
        private int sourceWidth;
        private int sourceHeight;

        public ExportFile(int version, String settings, String elements) {
            this(version, settings, elements, 0, 0);
        }

        public ExportFile(int version, String settings, String elements, int sourceWidth, int sourceHeight) {
            this.version = version;
            this.settings = settings;
            this.elements = elements;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.md5 = MathUtils.computeMD5(version + settings + elements);
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public String getSettings() {
            return settings;
        }

        public void setSettings(String settings) {
            this.settings = settings;
        }

        public String getElements() {
            return elements;
        }

        public void setElements(String elements) {
            this.elements = elements;
        }

        public String getMd5() {
            return md5;
        }

        public void setMd5(String md5) {
            this.md5 = md5;
        }

        public int getSourceWidth() {
            return sourceWidth;
        }

        public void setSourceWidth(int sourceWidth) {
            this.sourceWidth = sourceWidth;
        }

        public int getSourceHeight() {
            return sourceHeight;
        }

        public void setSourceHeight(int sourceHeight) {
            this.sourceHeight = sourceHeight;
        }
    }

    public class ContentValuesSerializer implements JsonSerializer<ContentValues>, JsonDeserializer<ContentValues> {

        @Override
        public JsonElement serialize(ContentValues src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<String, Object> entry : src.valueSet()) {
                Object value = entry.getValue();
                if (value instanceof Integer) {
                    jsonObject.addProperty(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    jsonObject.addProperty(entry.getKey(), (Long) value);
                } else if (value instanceof Double) {
                    jsonObject.addProperty(entry.getKey(), (Double) value);
                } else if (value instanceof String) {
                    jsonObject.addProperty(entry.getKey(), (String) value);
                } else if (value instanceof byte[]) {
                    // Serialize Blob as Base64 encoded string
                    String base64Blob = context.serialize(value).getAsString();
                    jsonObject.addProperty(entry.getKey(), base64Blob);
                }
                // Handle other types as needed
            }
            return jsonObject;
        }

        @Override
        public ContentValues deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            ContentValues contentValues = new ContentValues();
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                JsonElement jsonElement = entry.getValue();
                if (jsonElement.isJsonPrimitive()) {
                    JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
                    if (jsonPrimitive.isNumber()) {
                        // Determine if it's a Long or Double based on the value
                        if (jsonPrimitive.getAsString().contains(".")) {
                            contentValues.put(entry.getKey(), jsonPrimitive.getAsDouble());
                        } else {
                            contentValues.put(entry.getKey(), jsonPrimitive.getAsLong());
                        }
                    } else if (jsonPrimitive.isString()) {
                        contentValues.put(entry.getKey(), jsonPrimitive.getAsString());
                    }
                } else if (jsonElement.isJsonArray()) {
                    // Deserialize Blob from Base64 encoded string
                    byte[] blob = context.deserialize(jsonElement, byte[].class);
                    contentValues.put(entry.getKey(), blob);
                }
                // Handle other types as needed
            }
            return contentValues;
        }
    }


    private static final String DATABASE_NAME = "super_config.db";
    private static final int DATABASE_VERSION = 1;
    private SQLiteDatabase writableDataBase;
    private SQLiteDatabase readableDataBase;

    public SuperConfigDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        writableDataBase = getWritableDatabase();
        readableDataBase = getReadableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 更新 onCreate，添加新字段
        // 创建表格的SQL语句
        String createElementTable = "CREATE TABLE IF NOT EXISTS element (" +
                "_id INTEGER PRIMARY KEY, " +
                "element_id INTEGER," +
                "config_id INTEGER," +
                "element_type INTEGER," +
                "element_value TEXT," +
                "element_middle_value TEXT," +
                "element_up_value TEXT," +
                "element_down_value TEXT," +
                "element_left_value TEXT," +
                "element_right_value TEXT," +
                "element_layer INTEGER," +
                "element_mode INTEGER," +
                "element_sense INTEGER," +
                "element_central_x INTEGER," +
                "element_central_y INTEGER," +
                "element_width INTEGER," +
                "element_height INTEGER," +
                "element_area_width INTEGER," +
                "element_area_height INTEGER," +
                "element_text TEXT," +
                "element_click_text TEXT," +
                "element_background_icon TEXT," +
                "element_click_background_icon TEXT," +
                "element_radius INTEGER," +
                "element_opacity INTEGER," +
                "element_thick INTEGER," +
                "element_background_color INTEGER," +
                "element_color INTEGER," +
                "element_pressed_color INTEGER," +
                Element.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR + " INTEGER," +
                Element.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR + " INTEGER," +
                Element.COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT + " INTEGER," +
                "element_create_time INTEGER," +
                Element.COLUMN_INT_ELEMENT_FLAG1 + " INTEGER DEFAULT 1," +
                "extra_attributes TEXT" + // 添加一个名为 extra_attributes 的文本列
                ")";
        // 执行SQL语句
        db.execSQL(createElementTable);

        String createConfigTable = "CREATE TABLE IF NOT EXISTS config (" +
                "_id INTEGER PRIMARY KEY, " +
                "config_id INTEGER," +
                "config_name TEXT," +
                "touch_enable TEXT," +
                "touch_mode TEXT," +
                "touch_sense INTEGER," +
                "game_vibrator TEXT," +
                "button_vibrator TEXT," +
                "mouse_wheel_speed INTEGER," +
                PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH + " TEXT DEFAULT 'false'," +
                PageConfigController.COLUMN_INT_GLOBAL_OPACITY + " INTEGER DEFAULT 100," +
                PageConfigController.COLUMN_INT_GLOBAL_BORDER_COLOR + " INTEGER," +
                PageConfigController.COLUMN_INT_GLOBAL_TEXT_COLOR + " INTEGER," +
                "scheme_type TEXT DEFAULT 'game_key_mapping'," +
                "osc_vibrate INTEGER DEFAULT 1," +
                "osc_opacity INTEGER DEFAULT 90," +
                "osc_only_l3r3 INTEGER DEFAULT 0," +
                "osc_show_guide INTEGER DEFAULT 1," +
                "osc_half_height INTEGER DEFAULT 1," +
                "osc_flip_face_buttons INTEGER DEFAULT 0," +
                "osc_element_layout TEXT" +
                ")";

        db.execSQL(createConfigTable);
    }

    public void deleteTable(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + tableName);
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // MoonLink 重新开始，版本号从 1 开始，无升级路径
    }



    private void normalizeGlobalStyleSettings(ContentValues settingValues) {
        Long borderColor = settingValues.getAsLong(PageConfigController.COLUMN_INT_GLOBAL_BORDER_COLOR);
        if (borderColor != null && borderColor == -1L) {
            settingValues.putNull(PageConfigController.COLUMN_INT_GLOBAL_BORDER_COLOR);
        }

        Long textColor = settingValues.getAsLong(PageConfigController.COLUMN_INT_GLOBAL_TEXT_COLOR);
        if (textColor != null && textColor == -1L) {
            settingValues.putNull(PageConfigController.COLUMN_INT_GLOBAL_TEXT_COLOR);
        }
    }

    public void insertElement(ContentValues values) {
        writableDataBase.insert("element", null, values);
    }

    public void deleteElement(long configId, long elementId) {

        // 定义 WHERE 子句
        String selection = "config_id = ? AND element_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId), String.valueOf(elementId)};

        // 执行删除操作
        writableDataBase.delete("element", selection, selectionArgs);
    }

    public void updateElement(long configId, long elementId, ContentValues values) {

        // 定义 WHERE 子句
        String selection = "config_id = ? AND element_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId), String.valueOf(elementId)};

        writableDataBase.update(
                "element",   // 要更新的表
                values,    // 新值
                selection, // WHERE 子句
                selectionArgs // WHERE 子句中的占位符值
        );
    }

    public List<Long> queryAllElementIds(long configId) {

        // 定义要查询的列
        String[] projection = {"element_id", "element_layer"};

        // 定义 WHERE 子句
        String selection = "config_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId)};
        // 排序方式，增序
        String orderBy = "element_id + (element_layer * 281474976710656) ASC";

        // 执行查询
        Cursor cursor = readableDataBase.query(
                "element",   // 表名
                projection, // 要查询的列
                selection,  // WHERE 子句
                selectionArgs, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                orderBy  // 增序排序
        );

        List<Long> elementIds = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int elementIdIndex = cursor.getColumnIndexOrThrow("element_id");
                long elementId = cursor.getLong(elementIdIndex);
                elementIds.add(elementId);
            }
            cursor.close();
        }
        System.out.println("elementIds = " + elementIds);
        return elementIds;
    }

    public Object queryElementAttribute(long configId, long elementId, String elementAttribute) {

        // 定义要查询的列
        String[] projection = {elementAttribute};

        // 定义 WHERE 子句
        String selection = "config_id = ? AND element_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId), String.valueOf(elementId)};

        // 执行查询
        Cursor cursor = readableDataBase.query(
                "element",   // 表名
                projection, // 要查询的列
                selection,  // WHERE 子句
                selectionArgs, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                null  // 不排序
        );

        Object o = null;
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int columnIndex = cursor.getColumnIndexOrThrow(elementAttribute);
                switch (cursor.getType(columnIndex)) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        o = cursor.getLong(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        o = cursor.getDouble(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        o = cursor.getString(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        o = cursor.getBlob(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                        break;
                }

            }
            cursor.close();
        }

        return o;
    }

    public Map<String, Object> queryAllElementAttributes(long configId, long elementId) {
        Map<String, Object> resultMap = new HashMap<>();
        // 定义 WHERE 子句
        String selection = "config_id = ? AND element_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId), String.valueOf(elementId)};

        // 执行查询
        Cursor cursor = readableDataBase.query(
                "element",   // 表名
                null, // 要查询的列
                selection,  // WHERE 子句
                selectionArgs, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                null  // 不排序
        );
        if (cursor.moveToFirst()) {
            int columnCount = cursor.getColumnCount();
            for (int i = 0; i < columnCount; i++) {
                String columnName = cursor.getColumnName(i);
                int columnType = cursor.getType(i);

                switch (columnType) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        resultMap.put(columnName, cursor.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        resultMap.put(columnName, cursor.getString(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        resultMap.put(columnName, cursor.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        resultMap.put(columnName, cursor.getBlob(i));
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                        break;
                }
            }
        }
        cursor.close();
        return resultMap;
    }

    public void insertConfig(ContentValues values) {

        writableDataBase.insert("config", null, values);

    }

    public void deleteConfig(long configId) {

        // 定义 WHERE 子句
        String selection = "config_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId)};

        // 执行删除操作
        writableDataBase.delete("config", selection, selectionArgs);

        //删除element表中所有的config_id的element
        writableDataBase.delete("element", selection, selectionArgs);

    }

    public void updateConfig(long configId, ContentValues values) {

        // SQL WHERE 子句
        String selection = "config_id = ?";
        // selectionArgs 数组提供了 WHERE 子句中占位符 ? 的实际值
        String[] selectionArgs = {String.valueOf(configId)};

        writableDataBase.update(
                "config",   // 要更新的表
                values,    // 新值
                selection, // WHERE 子句
                selectionArgs // WHERE 子句中的占位符值
        );


    }

    public List<Long> queryAllConfigIds() {

        // 定义要查询的列
        String[] projection = {"config_id"};
        // 排序方式，增序
        String orderBy = "config_id ASC";
        // 执行查询
        Cursor cursor = readableDataBase.query(
                "config",   // 表名
                projection, // 要查询的列
                null,  // WHERE 子句
                null, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                orderBy  // 增序
        );

        List<Long> configIds = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int configIdIndex = cursor.getColumnIndexOrThrow("config_id");
                long configId = cursor.getLong(configIdIndex);
                configIds.add(configId);
            }
            cursor.close();
        }

        System.out.println("configIds = " + configIds);
        return configIds;
    }

    public Object queryConfigAttribute(long configId, String configAttribute, Object defaultValue) {

        // 定义要查询的列
        String[] projection = {configAttribute};

        // 定义 WHERE 子句
        String selection = "config_id = ?";
        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId)};

        // 执行查询
        Cursor cursor = readableDataBase.query(
                "config",   // 表名
                projection, // 要查询的列
                selection,  // WHERE 子句
                selectionArgs, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                null  // 不排序
        );

        Object o = null;
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int columnIndex = cursor.getColumnIndexOrThrow(configAttribute);
                switch (cursor.getType(columnIndex)) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        o = cursor.getLong(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        o = cursor.getDouble(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        o = cursor.getString(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        o = cursor.getBlob(columnIndex);
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                        break;
                }

            }
            cursor.close();
        }
        if (o == null) {
            return defaultValue;
        }
        return o;
    }

    public String exportConfig(Long configId) {
        List<ContentValues> elementsValueList = new ArrayList<>();
        ContentValues settingValues = new ContentValues();

        // 定义 WHERE 子句
        String selection = "config_id = ?";

        // 定义 WHERE 子句中的参数
        String[] selectionArgs = {String.valueOf(configId)};

        Cursor cursor = readableDataBase.query(
                "element",   // 表名
                null, // 要查询的列
                selection,  // WHERE 子句
                selectionArgs, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                null  // 不排序
        );

        // 遍历查询结果
        if (cursor.moveToFirst()) {
            do {
                ContentValues contentValues = new ContentValues();

                // 将当前行的所有数据存入 ContentValues
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    String columnName = cursor.getColumnName(i);
                    if (columnName.equals("_id")) {
                        continue;
                    }
                    int type = cursor.getType(i);

                    // 根据列的数据类型将其添加到 ContentValues
                    switch (type) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            contentValues.put(columnName, cursor.getLong(i));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            contentValues.put(columnName, cursor.getDouble(i));
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            contentValues.put(columnName, cursor.getString(i));
                            break;
                        case Cursor.FIELD_TYPE_BLOB:
                            contentValues.put(columnName, cursor.getBlob(i));
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            break;
                    }
                }

                // 将 ContentValues 对象添加到结果列表中
                elementsValueList.add(contentValues);

            } while (cursor.moveToNext());
        }


        cursor = readableDataBase.query(
                "config",   // 表名
                null, // 要查询的列
                selection,  // WHERE 子句
                selectionArgs, // WHERE 子句中的参数
                null, // 不分组
                null, // 不过滤
                null  // 不排序
        );

        if (cursor.moveToFirst()) {
            // 将当前行的所有数据存入 ContentValues
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String columnName = cursor.getColumnName(i);
                if (columnName.equals("_id")) {
                    continue;
                }
                int type = cursor.getType(i);

                // 根据列的数据类型将其添加到 ContentValues
                switch (type) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        settingValues.put(columnName, cursor.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        settingValues.put(columnName, cursor.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        settingValues.put(columnName, cursor.getString(i));
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        settingValues.put(columnName, cursor.getBlob(i));
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                        break;
                }
            }
        }

        // 关闭 Cursor
        cursor.close();

        // 获取当前设备屏幕尺寸，写入导出文件用于导入时坐标缩放
        int sourceWidth = 0, sourceHeight = 0;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                sourceWidth = metrics.widthPixels;
                sourceHeight = metrics.heightPixels;
            }
        } catch (Exception ignored) {}

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ContentValues.class, new ContentValuesSerializer());
        Gson gson = gsonBuilder.create();
        ContentValues[] elementsValues = elementsValueList.toArray(new ContentValues[0]);


        String settingString = gson.toJson(settingValues);
        String elementsString = gson.toJson(elementsValues);


        return gson.toJson(new ExportFile(DATABASE_VERSION, settingString, elementsString, sourceWidth, sourceHeight));


    }

    /**
     * 对元素列表应用屏幕坐标缩放。
     * 读取 ExportFile 中存储的源屏幕尺寸，与当前设备屏幕尺寸计算缩放比例后应用到各元素。
     */
    private void scaleElementsFromExport(int sourceWidth, int sourceHeight, ContentValues[] elements) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || elements == null || elements.length == 0) return;

        // 获取当前设备屏幕尺寸
        int targetWidth = 0, targetHeight = 0;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                targetWidth = metrics.widthPixels;
                targetHeight = metrics.heightPixels;
            }
        } catch (Exception ignored) {}

        if (targetWidth <= 0 || targetHeight <= 0) return;
        // 如果源和目标尺寸完全相同，跳过缩放
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) return;

        float scaleX = (float) targetWidth / (float) sourceWidth;
        float scaleY = (float) targetHeight / (float) sourceHeight;

        for (ContentValues el : elements) {
            // 位置
            Long cx = el.getAsLong(Element.COLUMN_INT_ELEMENT_CENTRAL_X);
            if (cx != null) {
                el.put(Element.COLUMN_INT_ELEMENT_CENTRAL_X, Math.round(cx * scaleX));
            }
            Long cy = el.getAsLong(Element.COLUMN_INT_ELEMENT_CENTRAL_Y);
            if (cy != null) {
                el.put(Element.COLUMN_INT_ELEMENT_CENTRAL_Y, Math.round(cy * scaleY));
            }
            // 尺寸
            Long w = el.getAsLong(Element.COLUMN_INT_ELEMENT_WIDTH);
            if (w != null) {
                el.put(Element.COLUMN_INT_ELEMENT_WIDTH, Math.max(1L, Math.round(w * scaleX)));
            }
            Long h = el.getAsLong(Element.COLUMN_INT_ELEMENT_HEIGHT);
            if (h != null) {
                el.put(Element.COLUMN_INT_ELEMENT_HEIGHT, Math.max(1L, Math.round(h * scaleY)));
            }
            // 半径
            Long radius = el.getAsLong(Element.COLUMN_INT_ELEMENT_RADIUS);
            if (radius != null) {
                float uniformScale = Math.min(scaleX, scaleY);
                el.put(Element.COLUMN_INT_ELEMENT_RADIUS, Math.max(1L, Math.round(radius * uniformScale)));
            }
        }
    }

    /**
     * 从 JSON 字符串导入一个完整的配置，包括设置和所有元素。
     * 此方法会为所有项创建新的ID，并智能地修复元素之间的引用关系（如GroupButton的子元素和WheelPad的组按键）。
     *
     * @param configString 包含配置信息的JSON字符串。
     * @return 0表示成功，负数表示不同的错误代码。
     */
    public int importConfig(String configString) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ContentValues.class, new ContentValuesSerializer());
        Gson gson = gsonBuilder.create();
        ExportFile exportFile;

        try {
            exportFile = gson.fromJson(configString, ExportFile.class);
        } catch (Exception e) {
            return -1; // -1: 文件格式错误
        }

        // MD5校验 (原始数据校验)
        if (!exportFile.getMd5().equals(MathUtils.computeMD5(exportFile.getVersion() + exportFile.getSettings() + exportFile.getElements()))) {
            return -2; // -2: 文件被篡改或损坏
        }

        // 仅接受当前版本（DATABASE_VERSION=1）的配置文件
        if (exportFile.getVersion() != DATABASE_VERSION) {
            return -3; // -3: 版本不匹配
        }

        // 从 exportFile 获取数据
        String settingString = exportFile.getSettings();
        String elementsString = exportFile.getElements();

        ContentValues settingValues = gson.fromJson(settingString, ContentValues.class);
        ContentValues[] elements = gson.fromJson(elementsString, ContentValues[].class);
        normalizeGlobalStyleSettings(settingValues);

        // ── 屏幕参数缩放：根据源屏幕与当前设备屏幕的比例换算元素坐标 ──
        scaleElementsFromExport(exportFile.getSourceWidth(), exportFile.getSourceHeight(), elements);

        // --- 预处理，建立从旧ID到内存中ContentValues对象的映射 ---
        Map<Long, ContentValues> oldIdToObjectMap = new HashMap<>();
        for (ContentValues element : elements) {
            if (element.containsKey(Element.COLUMN_LONG_ELEMENT_ID)) {
                oldIdToObjectMap.put(element.getAsLong(Element.COLUMN_LONG_ELEMENT_ID), element);
            }
        }

        // --- 插入新的配置 setting，并获取新的 configId ---
        Long newConfigId = System.currentTimeMillis();
        settingValues.put(PageConfigController.COLUMN_LONG_CONFIG_ID, newConfigId);
        insertConfig(settingValues);

        // --- 插入所有元素，并为它们分配新的ID ---
        // 这个过程会直接修改内存中的 ContentValues 对象，为后续的引用修复做准备。
        long elementIdCounter = System.currentTimeMillis();
        for (ContentValues element : elements) {
            element.put(Element.COLUMN_LONG_ELEMENT_ID, elementIdCounter++);
            element.put(Element.COLUMN_LONG_CONFIG_ID, newConfigId);
            insertElement(element);
        }

        // --- 统一修复所有元素的引用关系 ---
        // 遍历所有内存中的元素对象，检查它们是否需要修复引用字段。
        for (ContentValues element : elements) {

            // --- GroupButton 的子元素引用 ---
            if (element.containsKey(Element.COLUMN_INT_ELEMENT_TYPE) && element.getAsLong(Element.COLUMN_INT_ELEMENT_TYPE) == Element.ELEMENT_TYPE_GROUP_BUTTON) {
                StringBuilder newValue = new StringBuilder("-1");
                String[] oldChildIds = element.getAsString(Element.COLUMN_STRING_ELEMENT_VALUE).split(",");
                for (String oldChildIdStr : oldChildIds) {
                    if (oldChildIdStr.equals("-1") || oldChildIdStr.isEmpty()) continue;
                    try {
                        long oldChildId = Long.parseLong(oldChildIdStr);
                        ContentValues childObject = oldIdToObjectMap.get(oldChildId);
                        if (childObject != null) {
                            // 从子元素对象中获取它刚刚被分配的新ID
                            newValue.append(",").append(childObject.getAsLong(Element.COLUMN_LONG_ELEMENT_ID));
                        }
                    } catch (NumberFormatException e) { /* 忽略格式错误的ID */ }
                }
                element.put(Element.COLUMN_STRING_ELEMENT_VALUE, newValue.toString());
                updateElement(element.getAsLong(Element.COLUMN_LONG_CONFIG_ID), element.getAsLong(Element.COLUMN_LONG_ELEMENT_ID), element);
            }

            // --- WheelPad 的组按键引用 ---
            if (element.containsKey(Element.COLUMN_INT_ELEMENT_TYPE) && element.getAsLong(Element.COLUMN_INT_ELEMENT_TYPE) == Element.ELEMENT_TYPE_WHEEL_PAD) {
                String oldSegmentValues = element.getAsString(Element.COLUMN_STRING_ELEMENT_VALUE);
                if (oldSegmentValues == null || oldSegmentValues.isEmpty()) continue;

                String[] segments = oldSegmentValues.split(",");
                StringBuilder newSegmentValues = new StringBuilder();
                for (int i = 0; i < segments.length; i++) {
                    String segment = segments[i];
                    String valuePart = segment.split("\\|")[0];
                    String namePart = segment.contains("|") ? segment.substring(segment.indexOf("|")) : "";

                    if (valuePart.startsWith("gb")) {
                        try {
                            long oldGroupId = Long.parseLong(valuePart.substring(2));
                            ContentValues groupObject = oldIdToObjectMap.get(oldGroupId);
                            if (groupObject != null) {
                                // 从 GroupButton 对象中获取它刚刚被分配的新ID
                                newSegmentValues.append("gb").append(groupObject.getAsLong(Element.COLUMN_LONG_ELEMENT_ID)).append(namePart);
                            } else {
                                // 如果找不到对应的组按键，保留原始值或设为null
                                newSegmentValues.append(segment);
                            }
                        } catch (NumberFormatException e) {
                            newSegmentValues.append(segment); // 解析失败则保留原始值
                        }
                    } else {
                        newSegmentValues.append(segment); // 不是组按键引用，直接附加
                    }

                    if (i < segments.length - 1) {
                        newSegmentValues.append(",");
                    }
                }
                element.put(Element.COLUMN_STRING_ELEMENT_VALUE, newSegmentValues.toString());
                updateElement(element.getAsLong(Element.COLUMN_LONG_CONFIG_ID), element.getAsLong(Element.COLUMN_LONG_ELEMENT_ID), element);
            }
        }

        return 0; // 成功
    }

    public int mergeConfig(String configString, Long existConfigId) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ContentValues.class, new ContentValuesSerializer());
        Gson gson = gsonBuilder.create();
        ExportFile exportFile;

        try {
            exportFile = gson.fromJson(configString, ExportFile.class);
        } catch (Exception e) {
            return -1; // -1: 文件格式错误
        }

        // MD5校验
        if (!exportFile.getMd5().equals(MathUtils.computeMD5(exportFile.getVersion() + exportFile.getSettings() + exportFile.getElements()))) {
            return -2; // -2: 文件被篡改或损坏
        }

        // 仅接受当前版本（DATABASE_VERSION=1）的配置文件
        if (exportFile.getVersion() != DATABASE_VERSION) {
            return -3; // -3: 版本不匹配
        }

        // 从 exportFile 获取数据 (mergeConfig不需要settings)
        String elementsString = exportFile.getElements();

        ContentValues[] elements = gson.fromJson(elementsString, ContentValues[].class);

        // ── 屏幕参数缩放 ──
        scaleElementsFromExport(exportFile.getSourceWidth(), exportFile.getSourceHeight(), elements);

        // 将组按键及其子按键存储在MAP中
        Map<ContentValues, List<ContentValues>> groupButtonMaps = new HashMap<>();
        for (ContentValues groupButtonElement : elements) {
            if (groupButtonElement.containsKey(Element.COLUMN_INT_ELEMENT_TYPE) && (long) groupButtonElement.get(Element.COLUMN_INT_ELEMENT_TYPE) == Element.ELEMENT_TYPE_GROUP_BUTTON) {
                List<ContentValues> childElements = new ArrayList<>();

                String[] childElementStringIds = ((String) groupButtonElement.get(Element.COLUMN_STRING_ELEMENT_VALUE)).split(",");
                // 按键组的值，子按键们的ID
                for (String childElementStringId : childElementStringIds) {
                    long childElementId = Long.parseLong(childElementStringId);
                    for (ContentValues element : elements) {
                        if (element.containsKey(Element.COLUMN_LONG_ELEMENT_ID) && (long) element.get(Element.COLUMN_LONG_ELEMENT_ID) == childElementId) {
                            childElements.add(element);
                            break;
                        }
                    }
                }
                groupButtonMaps.put(groupButtonElement, childElements);

            }
        }

        // 更新所有按键的ID
        long elementId = System.currentTimeMillis();
        for (ContentValues contentValues : elements) {
            contentValues.put(Element.COLUMN_LONG_ELEMENT_ID, elementId++);
            contentValues.put(Element.COLUMN_LONG_CONFIG_ID, existConfigId);
            insertElement(contentValues);
        }

        // 更新组按键的值
        for (Map.Entry<ContentValues, List<ContentValues>> groupButtonMap : groupButtonMaps.entrySet()) {
            String newValue = "-1";
            for (ContentValues childElement : groupButtonMap.getValue()) {
                newValue = newValue + "," + childElement.get(Element.COLUMN_LONG_ELEMENT_ID);
            }
            ContentValues groupButton = groupButtonMap.getKey();
            groupButton.put(Element.COLUMN_STRING_ELEMENT_VALUE, newValue);
            updateElement((Long) groupButton.get(Element.COLUMN_LONG_CONFIG_ID),
                    (Long) groupButton.get(Element.COLUMN_LONG_ELEMENT_ID),
                    groupButton);
        }

        return 0;
    }


}

