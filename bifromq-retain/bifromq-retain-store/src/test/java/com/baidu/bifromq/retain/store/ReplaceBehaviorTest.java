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

package com.baidu.bifromq.retain.store;

import static org.testng.Assert.assertEquals;

import com.baidu.bifromq.retain.rpc.proto.MatchCoProcReply;
import com.baidu.bifromq.retain.rpc.proto.RetainCoProcReply;
import com.baidu.bifromq.type.TopicMessage;
import org.testng.annotations.Test;

public class ReplaceBehaviorTest extends RetainStoreTest {

    @Test(groups = "integration")
    public void replaceWithinLimit() {
        String tenantId = "tenantId";
        String topic = "/a";
        TopicMessage message = message(topic, "hello");
        TopicMessage message1 = message(topic, "world");
        assertEquals(requestRetain(tenantId, 1, message).getResult(),
            RetainCoProcReply.Result.RETAINED);

        assertEquals(requestRetain(tenantId, 1, message1).getResult(),
            RetainCoProcReply.Result.RETAINED);

        MatchCoProcReply matchReply = requestMatch(tenantId, topic, 10);
        assertEquals(matchReply.getMessagesCount(), 1);
        assertEquals(matchReply.getMessages(0), message1);

    }

    @Test(groups = "integration")
    public void replaceButExceedLimit() {
        String tenantId = "tenantId";
        assertEquals(requestRetain(tenantId, 2, message("/a", "hello")).getResult(),
            RetainCoProcReply.Result.RETAINED);

        TopicMessage message = message("/b", "world");
        assertEquals(requestRetain(tenantId, 2, message).getResult(),
            RetainCoProcReply.Result.RETAINED);

        // limit now shrink to 1
        assertEquals(requestRetain(tenantId, 1, message("/b", "!!!")).getResult(),
            RetainCoProcReply.Result.ERROR);

        MatchCoProcReply matchReply = requestMatch(tenantId, "/b", 10);
        assertEquals(matchReply.getMessagesCount(), 1);
        assertEquals(matchReply.getMessages(0), message);
    }
}
