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

package com.baidu.bifromq.starter.config;

import com.baidu.bifromq.baseenv.EnvProvider;
import com.baidu.bifromq.starter.config.model.AgentHostConfig;
import com.baidu.bifromq.starter.config.model.LocalSessionServerConfig;
import com.baidu.bifromq.starter.config.model.RPCClientConfig;
import com.baidu.bifromq.starter.config.model.ServerSSLContextConfig;
import com.baidu.bifromq.starter.config.model.SessionDictServerConfig;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MQTTServerConfig implements StarterConfig {
    private String host;

    private int connTimeoutSec = 20;

    private int maxConnPerSec = 1000;

    private int maxDisconnPerSec = 1000;

    private int maxMsgByteSize = 256 * 1024;

    private int maxResendTimes = 5;

    private int maxConnBandwidth = 512 * 1024;

    private int defaultKeepAliveSec = 300;

    private int qos2ConfirmWindowSec = 5;

    private int tcpPort = 1883;

    private int tlsPort = 1884;

    private int wsPort = 80;

    private int wssPort = 443;

    private String wsPath = "/mqtt";

    private boolean tcpEnabled = true;

    private boolean tlsEnabled = false;

    private boolean wsEnabled = true;

    private boolean wssEnabled = false;

    private String authProviderFQN = null;

    private String settingProviderFQN = null;

    private int mqttWorkerThreads = EnvProvider.INSTANCE.availableProcessors();

    private ServerSSLContextConfig brokerSSLCtxConfig;

    private AgentHostConfig agentHostConfig;

    private LocalSessionServerConfig localSessionServerConfig;

    private SessionDictServerConfig sessionDictServerConfig;

    private RPCClientConfig sessionDictClientConfig;

    private RPCClientConfig inboxReaderClientConfig;

    private RPCClientConfig distClientConfig;

    private RPCClientConfig retainServiceClientConfig;

}
