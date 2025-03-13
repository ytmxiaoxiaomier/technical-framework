package com.example.mq.support.util;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 属性工具类
 */
public class PropertyUtils {
    /**
     * 解析属性字符串数组为Map
     * 格式为: "key1=value1", "key2=value2"
     *
     * @param properties 属性字符串数组
     * @return 属性Map
     */
    public static Map<String, String> parseProperties(String[] properties) {
        Map<String, String> result = new HashMap<>();

        if (properties == null) {
            return result;
        }

        for (String property : properties) {
            if (!StringUtils.hasText(property)) {
                continue;
            }

            int index = property.indexOf('=');
            if (index > 0) {
                String key = property.substring(0, index).trim();
                String value = property.substring(index + 1).trim();

                if (StringUtils.hasText(key)) {
                    result.put(key, value);
                }
            }
        }

        return result;
    }

    /**
     * 将字符串属性转换为指定类型
     *
     * @param value 字符串值
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertValue(String value, Class<T> type) {
        if (value == null) {
            return null;
        }

        if (type == String.class) {
            return (T) value;
        } else if (type == Integer.class || type == int.class) {
            return (T) Integer.valueOf(value);
        } else if (type == Long.class || type == long.class) {
            return (T) Long.valueOf(value);
        } else if (type == Boolean.class || type == boolean.class) {
            return (T) Boolean.valueOf(value);
        } else if (type == Double.class || type == double.class) {
            return (T) Double.valueOf(value);
        } else if (type == Float.class || type == float.class) {
            return (T) Float.valueOf(value);
        } else if (type.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) type, value);
        }

        throw new IllegalArgumentException("不支持的类型转换: " + type.getName());
    }

    /**
     * 从Map中获取指定类型的属性值
     *
     * @param properties 属性Map
     * @param key        属性键
     * @param type       目标类型
     * @param <T>        目标类型
     * @return 属性值，如果不存在则返回null
     */
    public static <T> T getProperty(Map<String, Object> properties, String key, Class<T> type) {
        if (properties == null || key == null) {
            return null;
        }

        Object value = properties.get(key);
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        } else if (value instanceof String) {
            return convertValue((String) value, type);
        }

        throw new IllegalArgumentException("无法将属性值转换为指定类型: " + value.getClass().getName() + " -> " + type.getName());
    }

    /**
     * 从Map中获取指定类型的属性值，如果不存在则返回默认值
     *
     * @param properties   属性Map
     * @param key          属性键
     * @param type         目标类型
     * @param defaultValue 默认值
     * @param <T>          目标类型
     * @return 属性值，如果不存在则返回默认值
     */
    public static <T> T getProperty(Map<String, Object> properties, String key, Class<T> type, T defaultValue) {
        T value = getProperty(properties, key, type);
        return value != null ? value : defaultValue;
    }
} 