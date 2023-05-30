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

package com.baidu.bifromq.basekv;

import com.baidu.bifromq.basekv.proto.KVRangeDescriptor;
import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.proto.Range;
import com.baidu.bifromq.basekv.raft.proto.RaftNodeSyncState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class KVRangeSetting {
    public final KVRangeId id;
    public final long ver;
    public final Range range;
    public final String leader;
    public final List<String> followers;
    public final List<String> allReplicas;

    public KVRangeSetting(String leaderStoreId, KVRangeDescriptor desc) {
        id = desc.getId();
        ver = desc.getVer();
        range = desc.getRange();
        leader = leaderStoreId;
        List<String> followers = new ArrayList<>();
        List<String> allReplicas = new ArrayList<>();
        for (String v : desc.getConfig().getVotersList()) {
            if (desc.getSyncStateMap().get(v) == RaftNodeSyncState.Replicating) {
                if (!v.equals(leaderStoreId)) {
                    followers.add(v);
                }
                allReplicas.add(v);
            }
        }
        for (String v : desc.getConfig().getLearnersList()) {
            if (desc.getSyncStateMap().get(v) == RaftNodeSyncState.Replicating) {
                allReplicas.add(v);
            }
        }
        this.followers = Collections.unmodifiableList(followers);
        this.allReplicas = Collections.unmodifiableList(allReplicas);
    }

}
