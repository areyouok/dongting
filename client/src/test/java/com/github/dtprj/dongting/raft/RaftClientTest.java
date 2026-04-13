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
import com.github.dtprj.dongting.net.NetException;
import com.github.dtprj.dongting.net.NioClientConfig;
import com.github.dtprj.dongting.net.PeerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author huangli
 */
public class RaftClientTest {

    private static final Method CONNECT_TO_LEADER_CALLBACK = initConnectToLeaderCallbackMethod();

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

    @Test
    public void testConnectLeaderFailPublishesNewGroupBeforeFutureCompletion() {
        client = new TestRaftClient();
        client.pendingFirstLeaderQuery = true;
        client.start();
        client.clientAddNode("1,127.0.0.1:40001;2,127.0.0.1:40002;3,127.0.0.1:40003");
        client.getNode(1).peer.status = PeerStatus.connected;
        client.getNode(2).peer.status = PeerStatus.connected;
        client.getNode(3).peer.status = PeerStatus.connected;

        client.clientAddOrUpdateGroup(1, new int[]{1, 2, 3});

        GroupInfo firstGi = client.getGroup(1);
        assertNotNull(firstGi.leaderFuture);
        assertEquals(1, client.queryCount);

        AtomicReference<CompletableFuture<GroupInfo>> retryFutureRef = new AtomicReference<>();
        firstGi.leaderFuture.whenComplete((result, ex) -> {
            retryFutureRef.set(client.updateLeaderInfo(1, false));
        });

        invokeConnectToLeaderCallback(client, firstGi, client.getNode(2), new NetException("mock"));

        CompletableFuture<GroupInfo> retryFuture = retryFutureRef.get();
        GroupInfo currentGi = client.getGroup(1);
        assertNotNull(retryFuture);
        assertNotSame(firstGi.leaderFuture, retryFuture);
        assertFalse(retryFuture.isCompletedExceptionally());
        assertEquals(2, client.queryCount);
        assertSame(currentGi, retryFuture.join());
        assertSame(client.getNode(2), currentGi.leader);
        assertSame(null, currentGi.leaderFuture);
        assertFalse(currentGi.serversEpoch <= firstGi.serversEpoch);
    }

    private static Method initConnectToLeaderCallbackMethod() {
        try {
            Method method = RaftClient.class.getDeclaredMethod(
                    "connectToLeaderCallback", GroupInfo.class, RaftNode.class, Throwable.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void invokeConnectToLeaderCallback(RaftClient client, GroupInfo gi, RaftNode leader, Throwable ex) {
        try {
            CONNECT_TO_LEADER_CALLBACK.invoke(client, gi, leader, ex);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static class TestRaftClient extends RaftClient {
        private int queryCount;
        private boolean pendingFirstLeaderQuery;

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

            return pendingFirstLeaderQuery && queryCount == 1
                    ? new CompletableFuture<>()
                    : CompletableFuture.completedFuture(resp);
        }
    }
}
