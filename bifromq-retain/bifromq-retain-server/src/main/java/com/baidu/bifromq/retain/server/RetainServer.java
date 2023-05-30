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

package com.baidu.bifromq.retain.server;

import com.baidu.bifromq.basekv.client.IBaseKVStoreClient;
import com.baidu.bifromq.baserpc.IRPCServer;
import com.baidu.bifromq.plugin.settingprovider.ISettingProvider;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class RetainServer implements IRetainServer {
    private final String serviceUniqueName;
    private final IRPCServer rpcServer;
    private final RetainService retainService;

    RetainServer(String serviceUniqueName, ISettingProvider settingProvider,
                 IBaseKVStoreClient storeClient) {
        this.serviceUniqueName = serviceUniqueName;
        this.retainService = new RetainService(settingProvider, storeClient);
        this.rpcServer = buildRPCServer(retainService);
    }

    protected abstract IRPCServer buildRPCServer(RetainService distService);

    @Override
    public void start() {
        log.info("Starting retain server");
        log.debug("Starting rpc server");
        rpcServer.start();
        log.info("Retain server started");
    }

    @SneakyThrows
    @Override
    public void shutdown() {
        log.info("Shutting down retain server");
        log.debug("Shutting down rpc server");
        rpcServer.shutdown();
        log.info("Retain server stopped");
    }
}
