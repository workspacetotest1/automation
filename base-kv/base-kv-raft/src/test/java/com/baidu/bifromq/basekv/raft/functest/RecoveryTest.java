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

package com.baidu.bifromq.basekv.raft.functest;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.baidu.bifromq.basekv.raft.exception.RecoveryException;
import com.baidu.bifromq.basekv.raft.functest.annotation.Cluster;
import com.baidu.bifromq.basekv.raft.functest.annotation.Config;
import com.baidu.bifromq.basekv.raft.functest.template.SharedRaftConfigTestTemplate;
import com.baidu.bifromq.basekv.raft.proto.RaftNodeStatus;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class RecoveryTest extends SharedRaftConfigTestTemplate {


    @Test
    public void testRecoveryConditionNotSatisfied0() {
        // in 3 node cluster, member becomes candidate due to only partitioned from leader
        // the recovery task will be canceled after election timeout
        String leader = group.currentLeader().get();
        String follower = group.currentFollowers().get(0);
        log.info("Partition {} from {}", follower, leader);
        group.cut(leader, follower);
        await().until(() -> group.nodeState(follower) == RaftNodeStatus.Candidate
            || group.nodeState(follower) == RaftNodeStatus.Follower);
        try {
            group.recover(follower).join();
            fail();
        } catch (CompletionException e) {
            assertTrue(e.getCause() == RecoveryException.NOT_QUALIFY
                || e.getCause() == RecoveryException.NOT_LOST_QUORUM);
        }
    }

    @Cluster(v = "V1,V2,V3,V4")
    @Test
    public void testRecoveryConditionNotSatisfied1() {
        // in 4 node cluster, member becomes candidate due to only partitioned from leader only
        // the recovery task will be canceled when enough votes reply received
        testRecoveryConditionNotSatisfied0();
    }

    @Config(preVote = false)
    @Test
    public void testRecoveryConditionNotSatisfied2() {
        // in 3 node cluster with pre-vote disabled, member becomes candidate due to only partitioned from leader only
        // the recovery task will be canceled during election timeout
        testRecoveryConditionNotSatisfied0();
    }

    @Cluster(v = "V1,V2,V3,V4")
    @Config(preVote = false)
    @Test
    public void testRecoveryConditionNotSatisfied3() {
        // in 4 node cluster with pre-vote disabled, member becomes candidate due to only partitioned from leader only
        // the recovery task will be canceled during election timeout
        testRecoveryConditionNotSatisfied0();
    }

    @Test
    public void testRecoveryConditionNotSatisfied4() {
        // member becomes candidate due to being removed from cluster
        String leader = group.currentLeader().get();
        await().until(() -> group.currentCandidates().isEmpty());
        Set<String> newVoters = new HashSet<String>() {{
            add("V1");
            add("V2");
            add("V3");
        }};
        newVoters.remove(leader);
        group.changeClusterConfig(leader, "cId", newVoters, Collections.emptySet());
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);
        try {
            group.recover(leader).join();
            fail();
        } catch (CompletionException e) {
            log.info("{}", e.getCause().getMessage());
            assertTrue(e.getCause() == RecoveryException.NOT_VOTER);
        }
    }

    @Test
    public void testRecoveryConditionNotSatisfied5() {
        // normal cluster reject recovery operation
        String leader = group.currentLeader().get();
        try {
            group.recover(leader).join();
            fail();
        } catch (CompletionException e) {
            assertTrue(e.getCause() == RecoveryException.NOT_LOST_QUORUM);
        }
        for (String follower : group.currentFollowers()) {
            try {
                group.recover(follower).join();
                fail();
            } catch (CompletionException e) {
                assertTrue(e.getCause() == RecoveryException.NOT_LOST_QUORUM);
            }
        }
    }

    @Test
    public void testRecoveryConditionNotSatisfied6() {
        // candidate state because of leadership transferring
        String leader = group.currentLeader().get();
        String follower = group.currentFollowers().get(0);
        group.transferLeadership(leader, follower);
        group.await(2);
        assertTrue(group.nodeState(follower) == RaftNodeStatus.Candidate);
        group.pause();
        CompletableFuture<Void> recoverTask = group.recover(follower);
        group.resume();
        try {
            recoverTask.join();
            fail();
        } catch (CompletionException e) {
            assertTrue(e.getCause() == RecoveryException.NOT_LOST_QUORUM);
        }
    }

    @Test
    public void testRecoveryConditionNotSatisfied7() {
        // 3 member cluster with pre-check enabled candidate state due to being removed from cluster during isolation
        String leader = group.currentLeader().get();
        String isolated = group.currentFollowers().get(0);
        log.info("Isolate {}", isolated);
        group.isolate(isolated);
        await().until(() -> group.nodeState(isolated) == RaftNodeStatus.Candidate);

        Set<String> newVoters = new HashSet<String>() {{
            add("V1");
            add("V2");
            add("V3");
        }};
        newVoters.remove(isolated);
        log.info("Remove {} from cluster", isolated);
        group.changeClusterConfig(leader, "cId", newVoters, Collections.emptySet()).join();
        group.awaitIndexCommitted(leader, 2);
        // trigger an election to move to next term
        assertTrue(group.stepDown(leader));
        group.waitForNextElection();

        log.info("Recover network");
        group.integrate(isolated);
        log.info("Recover {}", isolated);
        CompletableFuture<Void> recoverTask = group.recover(isolated);
        try {
            recoverTask.join();
            fail();
        } catch (CompletionException e) {
            assertTrue(e.getCause() == RecoveryException.NOT_LOST_QUORUM);
        }
    }

    @Config(preVote = false)
    @Test
    public void testRecoveryConditionNotSatisfied8() {
        testRecoveryConditionNotSatisfied7();
    }

    @Test
    public void testRecoveryConditionMeet0() {
        // recovery single candidate cluster
        String leader = group.currentLeader().get();
        for (String follower : group.currentFollowers()) {
            group.removeRaftNode(follower);
        }
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);
        log.info("Recover {}", leader);
        group.recover(leader);
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Leader);
    }

    @Test
    public void testRecoveryConditionMeet1() {
        // node is being isolated for long time
        String leader = group.currentLeader().get();
        group.isolate(leader);
        group.waitForNextElection();
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);
        Assert.assertNotEquals(leader, group.currentLeader().get());
        log.info("Recover {}", leader);
        group.recover(leader);
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Leader);
    }

    @Cluster(v = "V1,V2,V3,V4")
    @Test
    public void testRecoveryConditionMeet2() {
        // recovery two candidates cluster with identical logs
        String leader = group.currentLeader().get();
        String failed1 = group.currentFollowers().get(0);
        String failed2 = group.currentFollowers().get(1);
        log.info("Fail {} and {}", failed1, failed2);
        group.removeRaftNode(failed1);
        group.removeRaftNode(failed2);

        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);
        log.info("Recover {}", leader);
        group.recover(leader);
        group.waitForNextElection();
        assertTrue(group.currentLeader().isPresent());
        Assert.assertEquals(1, group.currentFollowers().size());
    }

    @Cluster(v = "V1,V2,V3,V4")
    @Config(preVote = false)
    @Test
    public void testRecoveryConditionMeet3() throws InterruptedException {
        // recovery two candidates cluster with identical logs when pre-vote disabled
        String leader = group.currentLeader().get();
        String failed1 = group.currentFollowers().get(0);
        String failed2 = group.currentFollowers().get(1);
        log.info("Fail {} and {}", failed1, failed2);
        group.removeRaftNode(failed1);
        group.removeRaftNode(failed2);

        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate
            || group.nodeState(leader) == RaftNodeStatus.Follower);
        log.info("Recover {}", leader);
        // without pre-vote, candidate is not stable enough to recover
        while (true) {
            try {
                group.recover(leader).join();
                break;
            } catch (CompletionException e) {
                if (RecoveryException.NOT_QUALIFY != e.getCause()
                    && RecoveryException.ABORT != e.getCause() &&
                    RecoveryException.NOT_LOST_QUORUM != e.getCause()) {
                    fail(e.getCause().getMessage());
                } else {
                    log.info("Retry recover due to {}", e.getCause().getMessage());
                    Thread.sleep(100);
                }
            }
        }
        group.waitForNextElection();
        assertTrue(group.currentLeader().isPresent());
        Assert.assertEquals(1, group.currentFollowers().size());
    }

    @Cluster(v = "V1,V2,V3,V4")
    @Test
    public void testRecoveryConditionMeet4() {
        // recovery two candidates cluster with different logs
        String leader = group.currentLeader().get();
        String remain = group.currentFollowers().get(0);
        String failed1 = group.currentFollowers().get(1);
        String failed2 = group.currentFollowers().get(2);
        group.isolate(remain);
        await().until(() -> group.nodeState(remain) == RaftNodeStatus.Candidate);

        group.propose(leader, ByteString.EMPTY);
        group.awaitIndexCommitted(leader, 2);

        log.info("Fail {} and {}", failed1, failed2);
        group.removeRaftNode(failed1);
        group.removeRaftNode(failed2);

        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);

        group.integrate(remain);

        log.info("Recover less log candidate {}", leader);
        try {
            group.recover(remain).join();
            fail();
        } catch (CompletionException e) {
            assertEquals(RecoveryException.NOT_QUALIFY, e.getCause());
        }

        log.info("Recover {}", leader);
        group.recover(leader);
        group.waitForNextElection();
        assertTrue(group.currentLeader().isPresent());
        Assert.assertEquals(1, group.currentFollowers().size());
    }

    @Cluster(v = "V1,V2,V3,V4")
    @Config(preVote = false)
    @Test
    public void testRecoveryConditionMeet5() throws InterruptedException {
        // recovery two candidates cluster with different logs when prevote disabled
        String leader = group.currentLeader().get();
        String remain = group.currentFollowers().get(0);
        String failed1 = group.currentFollowers().get(1);
        String failed2 = group.currentFollowers().get(2);
        group.isolate(remain);
        await().until(() -> group.nodeState(remain) == RaftNodeStatus.Candidate);

        group.propose(leader, ByteString.EMPTY);
        group.awaitIndexCommitted(leader, 2);

        log.info("Fail {} and {}", failed1, failed2);
        group.removeRaftNode(failed1);
        group.removeRaftNode(failed2);

        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);

        group.integrate(remain);

        log.info("Recover less log candidate {}", leader);
        try {
            group.recover(remain).join();
            fail();
        } catch (CompletionException e) {
        }

        log.info("Recover {}", leader);
        while (true) {
            try {
                group.recover(leader).join();
                break;
            } catch (CompletionException e) {
                if (RecoveryException.NOT_QUALIFY != e.getCause()
                    && RecoveryException.ABORT != e.getCause() &&
                    RecoveryException.NOT_LOST_QUORUM != e.getCause()) {
                    fail(e.getCause().getMessage());
                } else {
                    log.info("Retry recover due to {}", e.getCause().getMessage());
                    Thread.sleep(100);
                }
            }
        }

        group.waitForNextElection();
        assertTrue(group.currentLeader().isPresent());
        Assert.assertEquals(1, group.currentFollowers().size());
    }

    @Test
    public void testDuplicateRecovery() {
        // duplicated recovery to same candidate
        String leader = group.currentLeader().get();
        group.currentFollowers().forEach(f -> group.isolate(f));
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Candidate);
        log.info("Recover {}", leader);
        group.recover(leader);
        try {
            group.recover(leader).join();
        } catch (Exception e) {
            Assert.assertEquals(RecoveryException.RECOVERY_IN_PROGRESS, e.getCause());
        }
        await().until(() -> group.nodeState(leader) == RaftNodeStatus.Leader);
    }
}
