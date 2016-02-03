/* The MIT License (MIT)
 * Copyright (c) 2015 YouView Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
     * @param original object to spy
     * @param <T> type of original and spied object
     * @return spied object, with all references to original object replaced with spied object
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
