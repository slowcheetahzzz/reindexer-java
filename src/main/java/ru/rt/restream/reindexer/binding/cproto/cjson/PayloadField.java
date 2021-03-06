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
package ru.rt.restream.reindexer.binding.cproto.cjson;

import java.util.List;

public class PayloadField {

    private final long type;

    private final String name;

    private final long offset;

    private final long size;

    private final boolean isArray;

    private final List<String> jsonPaths;

    public PayloadField(long type, String name, long offset, long size, boolean isArray, List<String> jsonPaths) {
        this.type = type;
        this.name = name;
        this.offset = offset;
        this.size = size;
        this.isArray = isArray;
        this.jsonPaths = jsonPaths;
    }

    public long getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }

    public boolean isArray() {
        return isArray;
    }

    public List<String> getJsonPaths() {
        return jsonPaths;
    }
}
