/*
 * Copyright (c) 2023. Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baidu.bifromq.basekv.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.store.IKVRangeStore;
import com.baidu.bifromq.basekv.store.exception.KVRangeException;
import com.baidu.bifromq.basekv.store.proto.KVRangeROReply;
import com.baidu.bifromq.basekv.store.proto.KVRangeRORequest;
import com.baidu.bifromq.basekv.store.proto.ReplyCode;
import com.baidu.bifromq.basekv.utils.KVRangeIdUtil;
import com.google.protobuf.ByteString;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QueryPipelineTest {
    @Mock
    private IKVRangeStore rangeStore;

    @Mock
    private ServerCallStreamObserver streamObserver;

    @Test
    public void get() {
        get(false);
        get(true);
    }

    private void get(boolean linearized) {
        QueryPipeline pipeline = new QueryPipeline(rangeStore, linearized, streamObserver);
        KVRangeId rangeId = KVRangeIdUtil.generate();
        ByteString getKey = ByteString.copyFromUtf8("get");
        KVRangeRORequest getRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(1)
            .setKvRangeId(rangeId)
            .setGetKey(getKey)
            .build();

        when(rangeStore.get(1, rangeId, getKey, linearized))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        KVRangeROReply getReply = pipeline.handleRequest("_", getRequest).join();

        assertEquals(1, getReply.getReqId());
        assertEquals(ReplyCode.Ok, getReply.getCode());
        assertFalse(getReply.getGetResult().hasValue());
    }

    @Test
    public void exist() {
        exist(false);
        exist(true);
    }

    private void exist(boolean linearized) {
        QueryPipeline pipeline = new QueryPipeline(rangeStore, linearized, streamObserver);
        KVRangeId rangeId = KVRangeIdUtil.generate();
        ByteString existKey = ByteString.copyFromUtf8("exist");
        KVRangeRORequest existRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(1)
            .setKvRangeId(rangeId)
            .setExistKey(existKey)
            .build();

        when(rangeStore.exist(1, rangeId, existKey, linearized))
            .thenReturn(CompletableFuture.completedFuture(true));

        KVRangeROReply existReply = pipeline.handleRequest("_", existRequest).join();

        assertEquals(1, existReply.getReqId());
        assertEquals(ReplyCode.Ok, existReply.getCode());
        assertTrue(existReply.getExistResult());
    }

    @Test
    public void queryCoProc() {
        queryCoProc(false);
        queryCoProc(true);
    }

    private void queryCoProc(boolean linearized) {
        QueryPipeline pipeline = new QueryPipeline(rangeStore, linearized, streamObserver);
        KVRangeId rangeId = KVRangeIdUtil.generate();
        ByteString coProcInput = ByteString.copyFromUtf8("coProc");
        KVRangeRORequest coProcRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(1)
            .setKvRangeId(rangeId)
            .setRoCoProcInput(coProcInput)
            .build();

        when(rangeStore.queryCoProc(1, rangeId, coProcInput, linearized))
            .thenReturn(CompletableFuture.completedFuture(ByteString.empty()));

        KVRangeROReply coProcReply = pipeline.handleRequest("_", coProcRequest).join();

        assertEquals(1, coProcReply.getReqId());
        assertEquals(ReplyCode.Ok, coProcReply.getCode());
        assertEquals(ByteString.empty(), coProcReply.getRoCoProcResult());
    }


    @Test
    public void multiQueries() {
        multiQueries(false);
        multiQueries(true);
    }

    private void multiQueries(boolean linearized) {
        QueryPipeline pipeline = new QueryPipeline(rangeStore, linearized, streamObserver);
        KVRangeId rangeId = KVRangeIdUtil.generate();
        int reqCount = 10;
        List<KVRangeRORequest> requests = new ArrayList<>();
        List<KVRangeROReply> replies = new ArrayList<>();
        List<CompletableFuture<KVRangeROReply>> replyFutures = new ArrayList<>();
        for (int i = 0; i < reqCount; i++) {
            ByteString getKey = ByteString.copyFromUtf8("get-" + i);
            KVRangeRORequest getRequest = KVRangeRORequest.newBuilder()
                .setReqId(i)
                .setVer(1)
                .setKvRangeId(rangeId)
                .setGetKey(getKey)
                .build();

            when(rangeStore.get(1, rangeId, getKey, linearized))
                .thenReturn(new CompletableFuture<Optional<ByteString>>()
                    .completeOnTimeout(Optional.empty(), ThreadLocalRandom.current().nextInt(0, 100),
                        TimeUnit.MILLISECONDS));
            requests.add(getRequest);
            replyFutures.add(pipeline.handleRequest("_", getRequest)
                .whenComplete((v, e) -> replies.add(v)));
        }
        CompletableFuture.allOf(replyFutures.toArray(new CompletableFuture[] {})).join();
        assertEquals(requests.size(), replies.size());
        for (int i = 0; i < reqCount; i++) {
            assertEquals(requests.get(i).getReqId(), replies.get(i).getReqId());
        }
    }

    @Test
    public void errorCodeConversion() {
        QueryPipeline pipeline = new QueryPipeline(rangeStore, false, streamObserver);
        KVRangeId rangeId = KVRangeIdUtil.generate();
        ByteString getKey = ByteString.copyFromUtf8("get");

        // bad version
        KVRangeRORequest getRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(1)
            .setKvRangeId(rangeId)
            .setGetKey(getKey)
            .build();
        when(rangeStore.get(1, rangeId, getKey, false))
            .thenReturn(CompletableFuture.failedFuture(new KVRangeException.BadVersion("bad version")));
        KVRangeROReply getReply = pipeline.handleRequest("_", getRequest).join();
        assertEquals(ReplyCode.BadVersion, getReply.getCode());

        // bad request
        getRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(2)
            .setKvRangeId(rangeId)
            .setGetKey(getKey)
            .build();
        when(rangeStore.get(2, rangeId, getKey, false))
            .thenReturn(CompletableFuture.failedFuture(new KVRangeException.BadRequest("bad request")));
        getReply = pipeline.handleRequest("_", getRequest).join();
        assertEquals(ReplyCode.BadRequest, getReply.getCode());

        // try later
        getRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(3)
            .setKvRangeId(rangeId)
            .setGetKey(getKey)
            .build();
        when(rangeStore.get(3, rangeId, getKey, false))
            .thenReturn(CompletableFuture.failedFuture(new KVRangeException.TryLater("try later")));
        getReply = pipeline.handleRequest("_", getRequest).join();
        assertEquals(ReplyCode.TryLater, getReply.getCode());

        // internal error
        getRequest = KVRangeRORequest.newBuilder()
            .setReqId(1)
            .setVer(4)
            .setKvRangeId(rangeId)
            .setGetKey(getKey)
            .build();
        when(rangeStore.get(4, rangeId, getKey, false))
            .thenReturn(CompletableFuture.failedFuture(new KVRangeException.InternalException("internal error")));
        getReply = pipeline.handleRequest("_", getRequest).join();
        assertEquals(ReplyCode.InternalError, getReply.getCode());
    }
}
