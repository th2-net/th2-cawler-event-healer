/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.processor.healer

import com.exactpro.cradle.CradleStorage
import com.exactpro.cradle.testevents.StoredTestEventId
import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.utils.event.EventBatcher
import com.exactpro.th2.processor.api.IProcessor
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import mu.KotlinLogging

typealias EventBuilder = com.exactpro.th2.common.event.Event

class Processor(
    private val cradleStore: CradleStorage,
    private val eventBatcher: EventBatcher,
    configuration: Settings
) : IProcessor {

    private val statusCache: Cache<StoredTestEventId, Any> = Caffeine.newBuilder()
        .maximumSize(configuration.maxCacheCapacity.toLong())
        .build()

    override fun handle(intervalEventId: EventID, event: Event) {
        if (event.status == EventStatus.SUCCESS || !event.hasParentId()) {
            return
        }

        var parentId: StoredTestEventId? = event.parentId.toStoredTestEventId()
        while (parentId != null) {
            if (statusCache.getIfPresent(parentId) === FAKE_OBJECT) {
                K_LOGGER.debug {
                    "The $parentId has been already updated for ${event.id.toStoredTestEventId()} event id"
                }
                parentId = null
            } else {
                //FIXME: event can't be exist
                val parentEvent = cradleStore.getTestEvent(parentId)

                parentId = if (parentEvent.isSuccess) {
                    cradleStore.updateEventStatus(parentEvent, false)
                    reportUpdateEvent(intervalEventId, parentEvent.id)
                    parentEvent.parentId
                } else {
                    K_LOGGER.debug {
                        "The ${parentEvent.id} has already has failed status for ${event.id.toStoredTestEventId()} event id"
                    }
                    null
                }

                statusCache.put(parentEvent.id, FAKE_OBJECT)
            }
        }
    }

    override fun close() {
        cradleStore.dispose()
    }

    private fun reportUpdateEvent(intervalEventId: EventID, eventId: StoredTestEventId) {
        // FIXME: Add link to updated event
        eventBatcher.onEvent(EventBuilder.start()
            .name("Updated status of $eventId")
            .type("Update status")
            .toProto(intervalEventId))
    }

    companion object {
        private val K_LOGGER = KotlinLogging.logger {}
        private val FAKE_OBJECT: Any = Object()
    }
}