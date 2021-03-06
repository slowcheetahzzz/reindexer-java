/*
 * Copyright 2020 Restream
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.rt.restream.reindexer.annotations;

import ru.rt.restream.reindexer.CollateMode;
import ru.rt.restream.reindexer.FieldType;
import ru.rt.restream.reindexer.IndexType;
import ru.rt.restream.reindexer.ReindexScanner;
import ru.rt.restream.reindexer.ReindexerIndex;
import ru.rt.restream.reindexer.util.BeanPropertyUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.rt.restream.reindexer.FieldType.BOOL;
import static ru.rt.restream.reindexer.FieldType.COMPOSITE;
import static ru.rt.restream.reindexer.FieldType.DOUBLE;
import static ru.rt.restream.reindexer.FieldType.INT;
import static ru.rt.restream.reindexer.FieldType.INT64;
import static ru.rt.restream.reindexer.FieldType.STRING;

public class ReindexAnnotationScanner implements ReindexScanner {

    private static final Map<Class<?>, FieldType> MAPPED_TYPES;

    static {
        MAPPED_TYPES = new HashMap<>();
        //Booleans
        MAPPED_TYPES.put(boolean.class, BOOL);
        MAPPED_TYPES.put(Boolean.class, BOOL);
        //Integers
        MAPPED_TYPES.put(byte.class, INT);
        MAPPED_TYPES.put(Byte.class, INT);
        MAPPED_TYPES.put(short.class, INT);
        MAPPED_TYPES.put(Short.class, INT);
        MAPPED_TYPES.put(int.class, INT);
        MAPPED_TYPES.put(Integer.class, INT);
        //Long
        MAPPED_TYPES.put(long.class, INT64);
        MAPPED_TYPES.put(Long.class, INT64);
        //Floats
        MAPPED_TYPES.put(float.class, DOUBLE);
        MAPPED_TYPES.put(Float.class, DOUBLE);
        MAPPED_TYPES.put(double.class, DOUBLE);
        MAPPED_TYPES.put(Double.class, DOUBLE);
        //String
        MAPPED_TYPES.put(String.class, STRING);
        MAPPED_TYPES.put(char.class, STRING);
        MAPPED_TYPES.put(Character.class, STRING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ReindexerIndex> parseIndexes(Class<?> itemClass) {
        return parseIndexes(itemClass, false, "", "",new HashMap<>());
    }

    List<ReindexerIndex> parseIndexes(Class<?> itemClass, boolean subArray, String reindexBasePath, String jsonBasePath,
                                      Map<String, List<Integer>> joined) {
        if (reindexBasePath.length() != 0 && !reindexBasePath.endsWith(".")) {
            reindexBasePath = reindexBasePath + ".";
        }

        if (jsonBasePath.length() != 0 && !jsonBasePath.endsWith(".")) {
            jsonBasePath = jsonBasePath + ".";
        }

        List<ReindexerIndex> indexes = new ArrayList<>();
        List<Field> fields = BeanPropertyUtils.getInheritedFields(itemClass);
        for (Field field : fields) {
            Reindex reindex = field.getAnnotation(Reindex.class);
            if (reindex == null || "-".equals(reindex.name()) || field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            String reindexPath = reindexBasePath + reindex.name();
            Json json = field.getAnnotation(Json.class);
            String jsonPath = jsonBasePath + (json == null ? field.getName() : json.value());
            FieldInfo fieldInfo = getFieldInfo(field);
            if (subArray) {
                fieldInfo.isArray = true;
            }
            if (COMPOSITE == fieldInfo.fieldType && !fieldInfo.isArray) {
                List<ReindexerIndex> nested = parseIndexes(field.getType(), true, reindexPath, jsonPath, joined);
                indexes.addAll(nested);
            } else if ((fieldInfo.isArray) && fieldInfo.componentType != null
                    && getFieldTypeByClass(fieldInfo.componentType) == COMPOSITE) {
                List<ReindexerIndex> nested = parseIndexes(fieldInfo.componentType, true, reindexPath, jsonPath, joined);
                indexes.addAll(nested);
            } else {
                String collate = reindex.collate();
                CollateMode collateMode = getCollateMode(collate);
                String sortOrder = getSortOrder(collateMode, collate);
                String precept = null;
                if (field.isAnnotationPresent(Serial.class)) {
                    if (!fieldInfo.isInt()) {
                        throw new IllegalStateException("@Serial is allowed only for int types (i.e INT, INT64)");
                    }
                    precept = field.getName() + "=serial()";
                }
                ReindexerIndex index = createIndex(reindexPath, Collections.singletonList(jsonPath), reindex.type(),
                        fieldInfo.fieldType, reindex.isDense(), reindex.isSparse(), reindex.isPrimaryKey(),
                        fieldInfo.isArray, collateMode, sortOrder, precept);
                indexes.add(index);
            }
        }

        Reindex[] composites = itemClass.getAnnotationsByType(Reindex.class);
        for (Reindex composite : composites) {
            String collate = composite.collate();
            CollateMode collateMode = getCollateMode(collate);
            String sortOrder = getSortOrder(collateMode, collate);
            ReindexerIndex compositeIndex = createIndex(String.join("+", composite.subIndexes()),
                    Arrays.asList(composite.subIndexes()), composite.type(), COMPOSITE, composite.isDense(),
                    composite.isSparse(), composite.isPrimaryKey(), false, collateMode, sortOrder, null);
            indexes.add(compositeIndex);
        }

        return indexes;
    }

    private String getSortOrder(CollateMode collateMode, String collate) {
        if (CollateMode.CUSTOM == collateMode) {
            return collate;
        }

        return "";
    }

    private CollateMode getCollateMode(String collate) {
        return Arrays.stream(CollateMode.values())
                .filter(cm -> cm.getName().equals(collate))
                .findFirst()
                .orElse(CollateMode.CUSTOM);
    }

    private ReindexerIndex createIndex(String reindexPath, List<String> jsonPath, IndexType indexType,
                                       FieldType fieldType, boolean isDense, boolean isSparse, boolean isPk,
                                       boolean isArray, CollateMode collateMode, String sortOrder, String precept) {
        ReindexerIndex index = new ReindexerIndex();
        index.setName(reindexPath);
        index.setSortOrder(sortOrder);
        index.setCollateMode(collateMode);
        index.setJsonPaths(Collections.singletonList(reindexPath));
        index.setDense(isDense);
        index.setSparse(isSparse);
        index.setPk(isPk);
        index.setArray(isArray);
        index.setJsonPaths(jsonPath);
        index.setIndexType(indexType);
        index.setFieldType(fieldType);
        index.setPrecept(precept);
        return index;
    }

    private FieldInfo getFieldInfo(Field field) {
        Class<?> type = field.getType();
        FieldInfo fieldInfo = new FieldInfo();
        fieldInfo.isArray = type.isArray() || Collection.class.isAssignableFrom(type);
        FieldType fieldType = null;
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            fieldType = getFieldTypeByClass(componentType);
            fieldInfo.componentType = componentType;
        } else if (field.getGenericType() instanceof ParameterizedType && fieldInfo.isArray) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Type typeArgument = parameterizedType.getActualTypeArguments()[0];
            if (typeArgument instanceof Class<?>) {
                Class<?> componentType = (Class<?>) typeArgument;
                fieldType = getFieldTypeByClass(componentType);
                fieldInfo.componentType = componentType;
            }
        } else {
            fieldType = getFieldTypeByClass(type);
        }

        if (fieldType == null) {
            throw new IllegalArgumentException("Unrecognized field type");
        }

        fieldInfo.fieldType = fieldType;
        return fieldInfo;
    }

    private FieldType getFieldTypeByClass(Class<?> type) {
        return MAPPED_TYPES.getOrDefault(type, COMPOSITE);
    }

    private static class FieldInfo {
        private FieldType fieldType;
        private boolean isArray;
        private Class<?> componentType;

        private boolean isInt() {
            return fieldType == INT || fieldType == INT64;
        }
    }

}
