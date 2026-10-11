package com.sdecloud.priorityflow.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.util.List;

/**
 * JSON 工具：统一使用 fastjson2。
 *
 * @author priority-flow
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * 序列化为 JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteByteArrayAsBase64);
    }

    /**
     * 反序列化对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    /**
     * 反序列化字符串列表（权限点/标签/提及人等 JSON 数组文本）
     */
    public static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.parseArray(json, String.class);
    }
}
