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

import static org.junit.Assert.assertEquals;

import com.baidu.bifromq.basekv.raft.proto.ClusterConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QuorumTrackerTest {
    @Mock
    IRaftNodeLogger logger;

    @Test
    public void testNonJointQuorumWithOddVoters() {
        ClusterConfig clusterConfig = ClusterConfig.newBuilder()
            .addVoters("V1")
            .addVoters("V2")
            .addVoters("V3")
            .build();
        QuorumTracker quorumTracker = new QuorumTracker(clusterConfig, logger);

        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Pending, 0, 0, 3);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Pending, 1, 0, 2);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", true);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Won, 2, 0, 1);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", true);
        quorumTracker.poll("V3", true);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Won, 3, 0, 0);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", false);
        quorumTracker.poll("V3", true);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Won, 2, 1, 0);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        quorumTracker.poll("V2", false);
        quorumTracker.poll("V3", true);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Lost, 1, 2, 0);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Pending, 0, 1, 2);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        quorumTracker.poll("V2", false);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Lost, 0, 2, 1);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        quorumTracker.poll("V2", false);
        quorumTracker.poll("V3", false);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Lost, 0, 3, 0);
    }

    @Test
    public void testNonJointQuorumWithEvenVoters() {
        ClusterConfig clusterConfig = ClusterConfig.newBuilder()
            .addVoters("V1")
            .addVoters("V2")
            .build();
        QuorumTracker quorumTracker = new QuorumTracker(clusterConfig, logger);

        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Pending, 0, 0, 2);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Pending, 1, 0, 1);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", true);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Won, 2, 0, 0);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", false);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Lost, 1, 1, 0);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        quorumTracker.poll("V2", false);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Lost, 0, 2, 0);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Lost, 0, 1, 1);
    }

    @Test
    public void testInvalidVote() {
        ClusterConfig clusterConfig = ClusterConfig.newBuilder()
            .addVoters("V1")
            .addVoters("V2")
            .build();
        QuorumTracker quorumTracker = new QuorumTracker(clusterConfig, logger);
        quorumTracker.poll("Fake", true);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Pending, 0, 0, 2);
    }

    @Test
    public void testEmptyQuorum() {
        ClusterConfig clusterConfig = ClusterConfig.newBuilder().build();
        QuorumTracker quorumTracker = new QuorumTracker(clusterConfig, logger);
        Assert.assertEquals(QuorumTracker.VoteResult.Won, quorumTracker.tally().result);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Won, 0, 0, 0);
        verifyVoteGroupResult(quorumTracker.tally().groupTwoResult, QuorumTracker.VoteResult.Won, 0, 0, 0);
    }

    @Test
    public void testJointQuorum() {
        ClusterConfig clusterConfig = ClusterConfig.newBuilder()
            .addVoters("V1")
            .addVoters("V2")
            .addVoters("V3")
            .addNextVoters("N1")
            .addNextVoters("N2")
            .addNextVoters("N3")
            .build();
        QuorumTracker quorumTracker = new QuorumTracker(clusterConfig, logger);
        quorumTracker.poll("V1", true); // pending
        quorumTracker.poll("N1", true); // pending
        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", true); // won
        quorumTracker.poll("N1", true); // pending
        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", true); // won
        quorumTracker.poll("N1", true);
        quorumTracker.poll("N2", true); // won
        Assert.assertEquals(QuorumTracker.VoteResult.Won, quorumTracker.tally().result);

        quorumTracker.reset();
        quorumTracker.poll("V1", true);
        quorumTracker.poll("V2", true); // won
        quorumTracker.poll("N1", false);
        quorumTracker.poll("N2", false); // lost
        Assert.assertEquals(QuorumTracker.VoteResult.Lost, quorumTracker.tally().result);

        quorumTracker.reset();
        quorumTracker.poll("V1", false);
        quorumTracker.poll("V2", false); // lost
        quorumTracker.poll("N1", false);
        quorumTracker.poll("N2", false); // lost
        Assert.assertEquals(QuorumTracker.VoteResult.Lost, quorumTracker.tally().result);
    }

    @Test
    public void testRefresh() {
        ClusterConfig clusterConfig = ClusterConfig.newBuilder()
            .addVoters("V1")
            .addVoters("V2")
            .addVoters("V3")
            .addNextVoters("N1")
            .addNextVoters("N2")
            .addNextVoters("N3")
            .build();
        QuorumTracker quorumTracker = new QuorumTracker(clusterConfig, logger);
        quorumTracker.poll("V1", true); // pending
        quorumTracker.poll("N1", true); // pending
        Assert.assertEquals(QuorumTracker.VoteResult.Pending, quorumTracker.tally().result);

        ClusterConfig clusterConfig1 = ClusterConfig.newBuilder()
            .addVoters("V1")
            .build();
        quorumTracker.refresh(clusterConfig1);
        Assert.assertEquals(QuorumTracker.VoteResult.Won, quorumTracker.tally().result);
        verifyVoteGroupResult(quorumTracker.tally().groupOneResult, QuorumTracker.VoteResult.Won, 1, 0, 0);
    }

    private void verifyVoteGroupResult(QuorumTracker.VoteGroupResult voteGroupResult,
                                       QuorumTracker.VoteResult result,
                                       int yes,
                                       int no,
                                       int missing) {

        assertEquals(result, voteGroupResult.result);
        assertEquals(yes, voteGroupResult.yes);
        assertEquals(no, voteGroupResult.no);
        assertEquals(missing, voteGroupResult.miss);
    }
}
