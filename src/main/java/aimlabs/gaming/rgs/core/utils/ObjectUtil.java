package aimlabs.gaming.rgs.core.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ObjectUtil {

    public static Type[] getActualTypeArguments(Class<?> clazz) {
        return ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments();
    }

    public static <T> T getValue(Object value, Class<T> clazz) {
        if (value == null) return null;
        return clazz.cast(value);
    }
}
