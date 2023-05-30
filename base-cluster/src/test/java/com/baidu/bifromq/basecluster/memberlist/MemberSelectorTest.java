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

package com.baidu.bifromq.basecluster.memberlist;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.baidu.bifromq.basecluster.membership.proto.HostEndpoint;
import com.baidu.bifromq.basecluster.messenger.IRecipient;
import com.google.common.util.concurrent.MoreExecutors;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MemberSelectorTest {
    @Mock
    private IHostMemberList memberList;
    @Mock
    private IHostAddressResolver addressResolver;
    private PublishSubject<Map<HostEndpoint, Integer>> membersSubject = PublishSubject.create();
    private Scheduler scheduler = Schedulers.from(MoreExecutors.directExecutor());

    @Before
    public void setup() {
        when(memberList.members()).thenReturn(membersSubject);
        when(addressResolver.resolve(Fixtures.LOCAL_ENDPOINT)).thenReturn(Fixtures.LOCAL_ADDR);
        when(addressResolver.resolve(Fixtures.REMOTE_HOST_1_ENDPOINT)).thenReturn(Fixtures.REMOTE_ADDR_1);
    }

    @Test
    public void noRecipients() {
        MemberSelector selector = new MemberSelector(memberList, scheduler, addressResolver);
        Collection<? extends IRecipient> recipients = selector.selectForSpread(10);
        assertTrue(recipients.isEmpty());
        assertTrue(selector.clusterSize() == 0);
    }

    @Test
    public void notEnoughRecipients() {
        MemberSelector selector = new MemberSelector(memberList, scheduler, addressResolver);
        membersSubject.onNext(new HashMap<>() {{
            put(Fixtures.LOCAL_ENDPOINT, 0);
            put(Fixtures.REMOTE_HOST_1_ENDPOINT, 0);
        }});
        Collection<? extends IRecipient> recipients = selector.selectForSpread(10);
        assertTrue(recipients.size() == 2);
        assertTrue(selector.clusterSize() == 2);
    }

    @Test
    public void selectRandomly() {
        MemberSelector selector = new MemberSelector(memberList, scheduler, addressResolver);
        membersSubject.onNext(new HashMap<>() {{
            put(Fixtures.LOCAL_ENDPOINT, 0);
            put(Fixtures.REMOTE_HOST_1_ENDPOINT, 0);
        }});
        await().until(() -> {
            Collection<? extends IRecipient> recipients = selector.selectForSpread(1);
            return recipients.size() == 1 && !recipients.stream().findFirst().get().addr().equals(Fixtures.LOCAL_ADDR);
        });
    }
}
