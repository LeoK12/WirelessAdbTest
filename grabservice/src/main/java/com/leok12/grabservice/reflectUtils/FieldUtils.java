/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.leok12.grabservice.reflectUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;


public class FieldUtils {

    private static Map<String, Field> sFieldCache = new HashMap<String, Field>();

    private static String getKey(Class<?> cls, String fieldName) {
        StringBuilder sb = new StringBuilder();
        sb.append(cls.toString()).append("#").append(fieldName);
        return sb.toString();
    }

    private static Field getField(Class<?> cls, String fieldName, final boolean forceAccess) {
        Validate.isTrue(cls != null, "The class must not be null");
        Validate.isTrue(!android.text.TextUtils.isEmpty(fieldName), "The field name must not be blank/empty");

        String key = getKey(cls, fieldName);
        Field cachedField;
        synchronized (sFieldCache) {
            cachedField = sFieldCache.get(key);
        }
        if (cachedField != null) {
            if (forceAccess && !cachedField.isAccessible()) {
                cachedField.setAccessible(true);
            }
            return cachedField;
        }

        // check up the superclass hierarchy
        for (Class<?> acls = cls; acls != null; acls = acls.getSuperclass()) {
            try {
                final Field field = acls.getDeclaredField(fieldName);
                // getDeclaredField checks for non-public scopes as well
                // and it returns accurate results
                if (!Modifier.isPublic(field.getModifiers())) {
                    if (forceAccess) {
                        field.setAccessible(true);
                    } else {
                        continue;
                    }
                }
                synchronized (sFieldCache) {
                    sFieldCache.put(key, field);
                }
                return field;
            } catch (final NoSuchFieldException ex) { // NOPMD
                // ignore
            }
        }
        // check the public interface case. This must be manually searched for
        // incase there is a public supersuperclass field hidden by a private/package
        // superclass field.
        Field match = null;
        for (final Class<?> class1 : Utils.getAllInterfaces(cls)) {
            try {
                final Field test = class1.getField(fieldName);
                Validate.isTrue(match == null, "Reference to field %s is ambiguous relative to %s"
                        + "; a matching field exists on two or more implemented interfaces.", fieldName, cls);
                match = test;
            } catch (final NoSuchFieldException ex) { // NOPMD
                // ignore
            }
        }
        synchronized (sFieldCache) {
            sFieldCache.put(key, match);
        }
        return match;
    }

    public static Object readField(final Field field, final Object target, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        if (forceAccess && !field.isAccessible()) {
            field.setAccessible(true);
        } else {
            MemberUtils.setAccessibleWorkaround(field);
        }
        return field.get(target);
    }


    public static void writeField(final Field field, final Object target, final Object value, final boolean forceAccess)
            throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        if (forceAccess && !field.isAccessible()) {
            field.setAccessible(true);
        } else {
            MemberUtils.setAccessibleWorkaround(field);
        }
        field.set(target, value);
    }


    public static Object readField(final Field field, final Object target) throws IllegalAccessException {
        return readField(field, target, true);
    }

    public static Field getField(final Class<?> cls, final String fieldName) {
        return getField(cls, fieldName, true);
    }


    public static Object readField(final Object target, final String fieldName) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        final Class<?> cls = target.getClass();
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate field %s on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        return readField(field, target, false);
    }

    public static Object readField(final Object target, final String fieldName, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        final Class<?> cls = target.getClass();
        final Field field = getField(cls, fieldName, forceAccess);
        Validate.isTrue(field != null, "Cannot locate field %s on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        return readField(field, target, forceAccess);
    }


    public static void writeField(final Object target, final String fieldName, final Object value) throws IllegalAccessException {
        writeField(target, fieldName, value, true);
    }

    public static void writeField(final Object target, final String fieldName, final Object value, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        final Class<?> cls = target.getClass();
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate declared field %s.%s", cls.getName(), fieldName);
        // already forced access above, don't repeat it here:
        writeField(field, target, value, forceAccess);
    }

    public static void writeField(final Field field, final Object target, final Object value) throws IllegalAccessException {
        writeField(field, target, value, true);
    }

    public static Object readStaticField(final Field field, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        Validate.isTrue(Modifier.isStatic(field.getModifiers()), "The field '%s' is not static", field.getName());
        return readField(field, (Object) null, forceAccess);
    }

    public static Object readStaticField(final Class<?> cls, final String fieldName) throws IllegalAccessException {
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate field '%s' on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        return readStaticField(field, true);
    }

    public static void writeStaticField(final Field field, final Object value, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        Validate.isTrue(Modifier.isStatic(field.getModifiers()), "The field %s.%s is not static", field.getDeclaringClass().getName(),
                field.getName());
        writeField(field, (Object) null, value, forceAccess);
    }


    public static void writeStaticField(final Class<?> cls, final String fieldName, final Object value) throws IllegalAccessException {
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate field %s on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        writeStaticField(field, value, true);
    }

    public static Field getDeclaredField(final Class<?> cls, final String fieldName, final boolean forceAccess) {
        Validate.isTrue(cls != null, "The class must not be null");
        Validate.isTrue(!android.text.TextUtils.isEmpty(fieldName), "The field name must not be blank/empty");
        try {
            // only consider the specified class by using getDeclaredField()
            final Field field = cls.getDeclaredField(fieldName);
            if (!MemberUtils.isAccessible(field)) {
                if (forceAccess) {
                    field.setAccessible(true);
                } else {
                    return null;
                }
            }
            return field;
        } catch (final NoSuchFieldException e) { // NOPMD
            // ignore
        }
        return null;
    }

    public static void writeDeclaredField(final Object target, final String fieldName, final Object value) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        final Class<?> cls = target.getClass();
        final Field field = getDeclaredField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate declared field %s.%s", cls.getName(), fieldName);
        // already forced access above, don't repeat it here:
        writeField(field, target, value, false);
    }


}
