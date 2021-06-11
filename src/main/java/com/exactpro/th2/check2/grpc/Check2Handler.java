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

import com.exactpro.th2.check2.cfg.Check2Configuration;
import com.exactpro.th2.common.grpc.ConnectionID;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.MessageID;
import com.exactpro.th2.crawlercheck2.grpc.Check2Grpc;
import com.exactpro.th2.crawlercheck2.grpc.Check2Info;
import com.exactpro.th2.crawlercheck2.grpc.CrawlerInfo;
import com.exactpro.th2.crawlercheck2.grpc.Response;
import com.exactpro.th2.dataprovider.grpc.EventData;
import com.exactpro.th2.dataprovider.grpc.MessageData;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Check2Handler extends Check2Grpc.Check2ImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(Check2Handler.class);

    private final Check2Configuration configuration;

    public Check2Handler(Check2Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void crawlerConnect(CrawlerInfo request, StreamObserver<Check2Info> responseObserver) {
        try {
            LOGGER.info("crawlerConnect request: {}", request);
            Check2Info response = Check2Info.newBuilder()
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
    public void sendEvent(EventData request, StreamObserver<Response> responseObserver) {
        try {
            LOGGER.info("sendEvent request: {}", request);
            Response response = Response.newBuilder()
                    .setEventId(EventID.newBuilder().setId("fake event id").build())
                    .build();
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
    public void sendMessage(MessageData request, StreamObserver<Response> responseObserver) {
        try {
            LOGGER.info("sendMessage request: {}", request);
            Response response = Response.newBuilder()
                    .setMessageId(MessageID.newBuilder()
                            .setConnectionId(ConnectionID.newBuilder().setSessionAlias("fake session alias").build())
                            .build())
                    .build();
            LOGGER.info("sendMessage response: {}", response);
            responseObserver.onNext(response);
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("sendMessage error: " + e, e);
        } finally {
            responseObserver.onCompleted();
        }
    }

}
