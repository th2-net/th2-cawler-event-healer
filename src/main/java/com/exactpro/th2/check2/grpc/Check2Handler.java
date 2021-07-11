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

package com.exactpro.th2.check2.grpc;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.intervals.Interval;
import com.exactpro.cradle.testevents.StoredTestEvent;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.cradle.testevents.TestEventToStore;
import com.exactpro.cradle.utils.CradleStorageException;
import com.exactpro.th2.check2.cfg.Check2Configuration;
import com.exactpro.th2.common.event.EventUtils;
import com.exactpro.th2.common.grpc.ConnectionID;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.common.grpc.MessageID;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerInfo;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceGrpc;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceInfo;
import com.exactpro.th2.crawler.dataservice.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataservice.grpc.EventResponse;
import com.exactpro.th2.crawler.dataservice.grpc.MessageDataRequest;
import com.exactpro.th2.crawler.dataservice.grpc.MessageResponse;
import com.exactpro.th2.dataprovider.grpc.EventData;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;


public class Check2Handler extends DataServiceGrpc.DataServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(Check2Handler.class);

    private final Check2Configuration configuration;
    private final CradleStorage storage;
    private final LinkedHashMap<String, StoredTestEventWrapper> cache;

    public Check2Handler(Check2Configuration configuration, CradleStorage storage) {
        this.configuration = configuration;
        this.storage = storage;
        this.cache = new LinkedHashMap<>(1000);
    }

    @Override
    public void crawlerConnect(CrawlerInfo request, StreamObserver<DataServiceInfo> responseObserver) {
        try {
            LOGGER.info("crawlerConnect request: {}", request);
            DataServiceInfo response = DataServiceInfo.newBuilder()
                    .setName(configuration.getName())
                    .setVersion(configuration.getVersion())
                    .build();
            LOGGER.info("crawlerConnect response: {}", response);
            responseObserver.onNext(response);
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("crawlerConnect error: " + e.getMessage(), e);
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendEvent(EventDataRequest request, StreamObserver<EventResponse> responseObserver) {
        try {
            LOGGER.info("sendEvent request: {}", request);

            String lastEventId = request.getEventDataList().get(request.getEventDataCount() - 1).getEventId().toString();

            heal(request.getEventDataList());

            EventResponse response = EventResponse.newBuilder().setId(EventID.newBuilder().setId(lastEventId).build()).build();

            LOGGER.info("sendEvent response: {}", response);
            responseObserver.onNext(response);
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("sendEvent error: " + e, e);
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendMessage(MessageDataRequest request, StreamObserver<MessageResponse> responseObserver) {
        try {
            LOGGER.info("sendMessage request: {}", request);
            MessageResponse response = MessageResponse.newBuilder()
                    .setId(MessageID.newBuilder()
                            .setConnectionId(ConnectionID.newBuilder().setSessionAlias("fake session alias").build())
                            .build())
                    .build();

            MessageID id = request.getMessageDataList().get(request.getMessageDataCount() - 1).getMessageId();

            LOGGER.info("ID of the last event from request: " + id);

            LOGGER.info("sendMessage response: {}", response);
            responseObserver.onNext(response);
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("sendMessage error: " + e, e);
        } finally {
            responseObserver.onCompleted();
        }
    }

    private void heal(Collection<EventData> events) throws IOException {
        List<StoredTestEventWrapper> eventAncestors;

        for (EventData event: events) {
            if (event.getSuccessful() == EventStatus.FAILED && event.hasParentEventId()) {

                eventAncestors = getAncestors(event);

                for (StoredTestEventWrapper ancestor : eventAncestors) {
                    if (ancestor.isSuccess()) {
                        storage.updateEventStatus(ancestor, false);
                        LOGGER.info("Event {} healed", ancestor.getId().toString());
                    }
                }

            }
        }
    }

    private List<StoredTestEventWrapper> getAncestors(EventData event) throws IOException {
        List<StoredTestEventWrapper> eventAncestors = new ArrayList<>();
        String parentId = event.getParentEventId().getId();

        while (parentId != null) {
            StoredTestEventWrapper parent;

            if (cache.containsKey(parentId)) {
                parent = cache.get(parentId);
            } else {
                parent = storage.getTestEvent(new StoredTestEventId(parentId));
                cache.put(parentId, parent);
            }

            eventAncestors.add(parent);

            parentId = parent.getParentId().toString();
        }

        return eventAncestors;
    }

}
