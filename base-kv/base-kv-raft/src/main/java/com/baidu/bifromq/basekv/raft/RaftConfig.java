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

package com.baidu.bifromq.basekv.raft;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Builder(toBuilder = true)
@Accessors(chain = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class RaftConfig {
    private int electionTimeoutTick = 10;
    private int heartbeatTimeoutTick = 1;
    private int installSnapshotTimeoutTick = 2000;
    private long maxSizePerAppend = 1024;
    // max inflight appends during replicating
    private int maxInflightAppends = 1024;
    // the max number of uncommitted proposals before rejection
    private int maxUncommittedProposals = 1024;
    private boolean preVote = true;
    private boolean readOnlyLeaderLeaseMode = true;
    private int readOnlyBatch = 10;
    private boolean disableForwardProposal = false;
    // if append log entries asynchronously which is an optimization described in $10.2.1 section of raft thesis
    private boolean asyncAppend = true;
}
