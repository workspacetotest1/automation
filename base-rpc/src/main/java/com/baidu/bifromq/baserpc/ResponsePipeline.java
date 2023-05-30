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

package com.baidu.bifromq.baserpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ResponsePipeline<RequestT, ResponseT> extends AbstractResponsePipeline<RequestT, ResponseT> {
    public ResponsePipeline(StreamObserver<ResponseT> responseObserver) {
        super(responseObserver);

    }

    @Override
    public final void onNext(RequestT request) {
        startHandlingRequest(request).thenAccept((response) -> emitResponse(request, response));
    }
}
