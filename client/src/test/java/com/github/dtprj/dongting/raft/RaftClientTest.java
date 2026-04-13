/*
 * Copyright The Dongting Project
 *
 * The Dongting Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.dtprj.dongting.raft;

import com.github.dtprj.dongting.common.TestUtil;
import com.github.dtprj.dongting.net.NioClientConfig;
import com.github.dtprj.dongting.net.PeerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author huangli
 */
public class RaftClientTest {

    private TestRaftClient client;

    @AfterEach
    public void afterTest() {
        TestUtil.stop(client);
        client = null;
    }

    @Test
    public void testAddGroupWithSyncLeaderQueryResult() {
        client = new TestRaftClient();
        client.start();
        client.clientAddNode("1,127.0.0.1:40001;2,127.0.0.1:40002;3,127.0.0.1:40003");
        client.getNode(1).peer.status = PeerStatus.connected;
        client.getNode(2).peer.status = PeerStatus.connected;
        client.getNode(3).peer.status = PeerStatus.connected;

        client.clientAddOrUpdateGroup(1, new int[]{1, 2, 3});

        GroupInfo gi = client.getGroup(1);
        assertEquals(1, gi.groupId);
        assertSame(client.getNode(2), gi.leader);
        assertEquals(1, client.queryCount);
    }

    private static class TestRaftClient extends RaftClient {
        private int queryCount;

        private TestRaftClient() {
            super(new RaftClientConfig(), new NioClientConfig("TestRaftClient"));
        }

        @Override
        protected CompletableFuture<QueryStatusResp> queryRaftServerStatus(int nodeId, int groupId) {
            queryCount++;

            QueryStatusResp resp = new QueryStatusResp();
            resp.groupId = groupId;
            resp.nodeId = nodeId;
            resp.leaderId = 2;
            resp.setFlag(true, false, true, false);

            return CompletableFuture.completedFuture(resp);
        }
    }
}
