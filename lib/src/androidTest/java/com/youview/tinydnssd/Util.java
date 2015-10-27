package com.youview.tinydnssd;

import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

class Util {
    /**
     * Spies an object and additionally replaces all references to the original object with the
     * spied object throughout its reference graph. This is useful where the original object uses a
     * cycle of references, such as an inner class pointing back the the parent, which could
     * otherwise lead back to the original object instead of the spied object.
     * @param original
     * @param <T>
     * @return
     */
    static <T> T powerSpy(T original) {
        T result = Mockito.spy(original);
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        seen.add(original);
        seen.add(result);
        replaceRefs(result, original.getClass(), original, result, seen);
        return result;
    }

    private static void replaceRefs(Object object, Class clazz, Object from, Object to, Set<Object> seen) {
        do {
            // TODO this doesn't yet iterate over array contents
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType().isPrimitive()) {
                    continue;
                }
                boolean access = field.isAccessible();
                if (!access) {
                    field.setAccessible(true);
                }
                try {
                    Object value = field.get(object);
                    if (value == from) {
                        field.set(object, to);
                    } else if (value != null && seen.add(value)) {
                        replaceRefs(value, value.getClass(), from, to, seen);
                    }
                } catch (IllegalAccessException e) {
                    // not expected since we called setAccessible(true)
                    throw new Error(e);
                } finally {
                    if (!access) {
                        field.setAccessible(false);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
    }
}
