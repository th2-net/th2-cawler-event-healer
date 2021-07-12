/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.check2.cfg;

import java.util.Objects;

public class Check2Configuration {

    private final String name;
    private final String version;
    private final int cacheSize;

    public Check2Configuration(String name, String version, int cacheSize) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.version = Objects.requireNonNull(version, "version is required");
        this.cacheSize = cacheSize;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public int getCacheSize() { return cacheSize; }
}
