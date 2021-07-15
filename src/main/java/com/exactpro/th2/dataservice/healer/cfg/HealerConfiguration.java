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

package com.exactpro.th2.dataservice.healer.cfg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class HealerConfiguration {


    private final String name;
    private final String version;
    private final int maxCacheCapacity;

    @JsonCreator
    public HealerConfiguration(@JsonProperty("name") String name,
                               @JsonProperty("version") String version,
                               @JsonProperty("maxCacheCapacity") int maxCacheCapacity) {
        this.name = Objects.requireNonNull(name, "Name is required");
        this.version = Objects.requireNonNull(version, "Version is required");

        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name of Healer cannot be empty");
        }

        if (version.trim().isEmpty()) {
            throw new IllegalArgumentException("Version of Healer cannot be empty");
        }

        if (maxCacheCapacity <= 0)
            throw new IllegalArgumentException("Size of cache cannot be negative or zero");

        this.maxCacheCapacity = maxCacheCapacity;
    }

    public String getName() { return name; }

    public String getVersion() { return version; }

    public int getMaxCacheCapacity() { return maxCacheCapacity; }
}
