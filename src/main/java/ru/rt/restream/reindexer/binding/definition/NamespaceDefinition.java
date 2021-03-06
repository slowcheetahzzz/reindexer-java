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
package ru.rt.restream.reindexer.binding.definition;

import ru.rt.restream.reindexer.ReindexerNamespace;

/**
 * Data-transfer object class, which is used to create namespace.
 */
public class NamespaceDefinition {

    private final String name;

    private final StorageOptions storage;

    public NamespaceDefinition(String name, StorageOptions storage) {
        this.name = name;
        this.storage = storage;
    }

    public static NamespaceDefinition fromNamespace(ReindexerNamespace<?> namespace) {
        StorageOptions storageOptions = new StorageOptions(namespace.isEnableStorage(),
                namespace.isDropStorageOnFileFormatError(), namespace.isCreateStorageIfMissing());
        return new NamespaceDefinition(namespace.getName(), storageOptions);
    }

    public String getName() {
        return name;
    }

    public StorageOptions getStorage() {
        return storage;
    }

    public static class StorageOptions {

        private final boolean enabled;

        private final boolean dropOnFileFormatError;

        private final boolean createIfMissing;

        public StorageOptions(boolean enabled, boolean dropOnFileFormatError, boolean createIfMissing) {
            this.enabled = enabled;
            this.dropOnFileFormatError = dropOnFileFormatError;
            this.createIfMissing = createIfMissing;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isDropOnFileFormatError() {
            return dropOnFileFormatError;
        }

        public boolean isCreateIfMissing() {
            return createIfMissing;
        }
    }

}
