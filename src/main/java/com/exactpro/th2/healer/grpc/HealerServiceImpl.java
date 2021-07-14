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

package com.exactpro.th2.healer.grpc;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.th2.healer.cache.EventsCache;
import com.exactpro.th2.healer.cfg.HealerConfiguration;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerId;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerInfo;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceGrpc;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceInfo;
import com.exactpro.th2.crawler.dataservice.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataservice.grpc.EventResponse;
import com.exactpro.th2.crawler.dataservice.grpc.Status;
import com.exactpro.th2.dataprovider.grpc.EventData;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.common.message.MessageUtils.toJson;


public class HealerServiceImpl extends DataServiceGrpc.DataServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealerServiceImpl.class);

    private final HealerConfiguration configuration;
    private final CradleStorage storage;
    private final Map<String, InnerEvent> cache;
    private final Set<CrawlerId> knownCrawlers = ConcurrentHashMap.newKeySet();

    public HealerServiceImpl(HealerConfiguration configuration, CradleStorage storage) {
        this.configuration = Objects.requireNonNull(configuration, "Configuration cannot be null");
        this.storage = Objects.requireNonNull(storage, "Cradle storage cannot be null");
        this.cache = new EventsCache<>(configuration.getMaxCacheCapacity());
    }

    @Override
    public void crawlerConnect(CrawlerInfo request, StreamObserver<DataServiceInfo> responseObserver) {
        try {

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("crawlerConnect request: {}", toJson(request, true));
            }


            knownCrawlers.add(request.getId());

            DataServiceInfo response = DataServiceInfo.newBuilder()
                    .setName(configuration.getName())
                    .setVersion(configuration.getVersion())
                    .build();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("crawlerConnect response: {}", toJson(response, true));
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("crawlerConnect error: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendEvent(EventDataRequest request, StreamObserver<EventResponse> responseObserver) {
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("sendEvent request: {}", toJson(request, true));
            }

            if (!knownCrawlers.contains(request.getId())) {

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Received request from unknown crawler with id {}. Sending response with HandshakeRequired = true", toJson(request.getId(), true));
                }

                responseObserver.onNext(EventResponse.newBuilder()
                        .setStatus(Status.newBuilder().setHandshakeRequired(true))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            int eventsCount = request.getEventDataCount();

            heal(request.getEventDataList());

            EventID lastEventId = null;

            if (eventsCount > 0) {
                lastEventId = request.getEventDataList().get(eventsCount - 1).getEventId();
            }

            EventResponse.Builder builder = EventResponse.newBuilder();

            if (lastEventId != null) {
                builder.setId(lastEventId);
            }

            EventResponse response = builder.build();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("sendEvent response: {}", toJson(response, true));
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("sendEvent error: " + e, e);
        }
    }

    private void heal(Collection<EventData> events) throws IOException {
        List<InnerEvent> eventAncestors;

        for (EventData event: events) {
            if (event.getSuccessful() == EventStatus.FAILED && event.hasParentEventId()) {

                eventAncestors = getAncestors(event);

                for (InnerEvent ancestor : eventAncestors) {
                    StoredTestEventWrapper ancestorEvent = ancestor.event;

                    if (ancestor.success) {
                        storage.updateEventStatus(ancestorEvent, false);
                        ancestor.markFailed();
                        LOGGER.info("Event {} healed", ancestorEvent.getId());
                    }
                }

            }
        }
    }

    private List<InnerEvent> getAncestors(EventData event) throws IOException {
        List<InnerEvent> eventAncestors = new ArrayList<>();
        String parentId = event.getParentEventId().getId();

        while (parentId != null) {
            InnerEvent innerEvent;

            if (cache.containsKey(parentId)) {
                innerEvent = cache.get(parentId);
            } else {
                StoredTestEventWrapper parent = storage.getTestEvent(new StoredTestEventId(parentId));

                innerEvent = new InnerEvent(parent, parent.isSuccess());
                cache.put(parentId, innerEvent);
            }

            eventAncestors.add(innerEvent);

            if (!innerEvent.success) break;

            parentId = innerEvent.event.getParentId().toString();
        }

        return eventAncestors;
    }

    private static class InnerEvent {
        private final StoredTestEventWrapper event;
        private volatile boolean success;

        private InnerEvent(StoredTestEventWrapper event, boolean success) {
            this.event = event;
            this.success = success;
        }

        private void markFailed() { this.success = false; }
    }

}
