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

package com.baidu.bifromq.plugin.eventcollector.mqttbroker.clientdisconnect;


import com.baidu.bifromq.plugin.eventcollector.EventType;
import com.baidu.bifromq.type.QoS;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@ToString(callSuper = true)
public final class PubPermissionCheckError extends ClientDisconnectEvent<PubPermissionCheckError> {
    private Throwable cause;

    private String topic;

    private QoS qos;

    private boolean retain;

    @Override
    public EventType type() {
        return EventType.PUB_PERMISSION_CHECK_ERROR;
    }

    @Override
    public void clone(PubPermissionCheckError orig) {
        super.clone(orig);
        this.cause = orig.cause;
        this.topic = orig.topic;
        this.qos = orig.qos;
        this.retain = orig.retain;
    }
}
