/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.distributed.dht;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.GridDirectTransient;
import org.apache.ignite.internal.managers.discovery.GridDiscoveryManager;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.GridCacheMessage;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.util.tostring.GridToStringInclude;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.jetbrains.annotations.NotNull;

/**
 * Affinity assignment response.
 */
public class GridDhtAffinityAssignmentResponse extends GridCacheMessage {
    /** */
    private static final long serialVersionUID = 0L;

    /** Topology version. */
    private AffinityTopologyVersion topVer;

    /** Affinity assignment. */
    @GridDirectTransient
    @GridToStringInclude
    private List<List<ClusterNode>> affAssignment;

    /** Affinity assignment bytes. */
    private byte[] affAssignmentBytes;

    /** */
    @GridDirectTransient
    private List<List<UUID>> affAssignmentIds;

    /** */
    private byte[] affAssignmentIdsBytes;

    /** */
    @GridDirectTransient
    private List<List<UUID>> idealAffAssignment;

    /** Affinity assignment bytes. */
    private byte[] idealAffAssignmentBytes;

    /**
     * Empty constructor.
     */
    public GridDhtAffinityAssignmentResponse() {
        // No-op.
    }

    /**
     * @param cacheId Cache ID.
     * @param topVer Topology version.
     * @param affAssignment Affinity assignment.
     * @param sndNodeIds If {@code true} sends only node IDs instead of nodes.
     */
    public GridDhtAffinityAssignmentResponse(int cacheId,
        @NotNull AffinityTopologyVersion topVer,
        List<List<ClusterNode>> affAssignment,
        boolean sndNodeIds) {
        this.cacheId = cacheId;
        this.topVer = topVer;

        if (!sndNodeIds)
            this.affAssignment = affAssignment;
        else
            affAssignmentIds = ids(affAssignment);
    }

    /** {@inheritDoc} */
    @Override public boolean partitionExchangeMessage() {
        return true;
    }

    /**
     * @return Topology version.
     */
    @Override public AffinityTopologyVersion topologyVersion() {
        return topVer;
    }

    /**
     * @param disco Discovery manager.
     * @return Affinity assignment.
     */
    public List<List<ClusterNode>> affinityAssignment(GridDiscoveryManager disco) {
        if (affAssignment != null)
            return affAssignment;

        if (affAssignmentIds != null)
            affAssignment = nodes(disco, affAssignmentIds);

        return affAssignment;
    }

    /**
     * @return Ideal affinity assignment.
     */
    public List<List<ClusterNode>> idealAffinityAssignment(GridDiscoveryManager disco) {
        return nodes(disco, idealAffAssignment);
    }

    /**
     * @param disco Discovery manager.
     * @param assignmentIds Assignment node IDs.
     * @return Assignment nodes.
     */
    private List<List<ClusterNode>> nodes(GridDiscoveryManager disco, List<List<UUID>> assignmentIds) {
        if (assignmentIds != null) {
            List<List<ClusterNode>> assignment = new ArrayList<>(assignmentIds.size());

            for (int i = 0; i < assignmentIds.size(); i++) {
                List<UUID> ids = assignmentIds.get(i);
                List<ClusterNode> nodes = new ArrayList<>(ids.size());

                for (int j = 0; j < ids.size(); j++) {
                    ClusterNode node = disco.node(topVer, ids.get(j));

                    assert node != null;

                    nodes.add(node);
                }

                assignment.add(nodes);
            }

            return assignment;
        }

        return null;
    }

    /**
     * @param idealAffAssignment Ideal affinity assignment.
     */
    public void idealAffinityAssignment(List<List<ClusterNode>> idealAffAssignment) {
        this.idealAffAssignment = ids(idealAffAssignment);
    }

    /**
     * @param assignments Assignment.
     */
    private List<List<UUID>> ids(List<List<ClusterNode>> assignments) {
        if (assignments != null) {
            List<List<UUID>> assignment = new ArrayList<>(assignments.size());

            for (int i = 0; i < assignments.size(); i++) {
                List<ClusterNode> nodes = assignments.get(i);
                List<UUID> ids = new ArrayList<>(nodes.size());

                for (int j = 0; j < nodes.size(); j++)
                    ids.add(nodes.get(j).id());

                assignment.add(ids);
            }

            return assignment;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return 29;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 7;
    }

    /**
     * @param ctx Context.
     */
    @Override public void prepareMarshal(GridCacheSharedContext ctx) throws IgniteCheckedException {
        super.prepareMarshal(ctx);

        assert affAssignment != null ^ affAssignmentIds != null;

        if (affAssignment != null && affAssignmentBytes == null)
            affAssignmentBytes = U.marshal(ctx, affAssignment);

        if (affAssignmentIds != null && affAssignmentIdsBytes == null)
            affAssignmentIdsBytes = U.marshal(ctx, affAssignmentIds);

        if (idealAffAssignment != null && idealAffAssignmentBytes == null)
            idealAffAssignmentBytes = U.marshal(ctx, idealAffAssignment);
    }

    /** {@inheritDoc} */
    @Override public void finishUnmarshal(GridCacheSharedContext ctx, ClassLoader ldr) throws IgniteCheckedException {
        super.finishUnmarshal(ctx, ldr);

        assert affAssignmentBytes != null ^ affAssignmentIdsBytes != null;

        ldr = U.resolveClassLoader(ldr, ctx.gridConfig());

        if (affAssignmentBytes != null && affAssignment == null)
            affAssignment = unmarshalNodes(affAssignmentBytes, ctx, ldr);

        if (affAssignmentIdsBytes != null && affAssignmentIds == null)
            affAssignmentIds = U.unmarshal(ctx, affAssignmentIdsBytes, ldr);

        if (idealAffAssignmentBytes != null && idealAffAssignment == null)
            idealAffAssignment = U.unmarshal(ctx, idealAffAssignmentBytes, ldr);
    }

    /**
     * @param bytes Assignment bytes.
     * @param ctx Context.
     * @param ldr Class loader.
     * @return Assignment.
     * @throws IgniteCheckedException If failed.
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private List<List<ClusterNode>> unmarshalNodes(byte[] bytes,
        GridCacheSharedContext ctx,
        ClassLoader ldr)
        throws IgniteCheckedException
    {
        List<List<ClusterNode>> affAssignment = U.unmarshal(ctx, bytes,
            U.resolveClassLoader(ldr, ctx.gridConfig()));

        // TODO IGNITE-2110: setting 'local' for nodes not needed when IGNITE-2110 is implemented.
        int assignments = affAssignment.size();

        for (int n = 0; n < assignments; n++) {
            List<ClusterNode> nodes = affAssignment.get(n);

            int size = nodes.size();

            for (int i = 0; i < size; i++) {
                ClusterNode node = nodes.get(i);

                if (node instanceof TcpDiscoveryNode)
                    ((TcpDiscoveryNode)node).local(node.id().equals(ctx.localNodeId()));
            }
        }

        return affAssignment;
    }

    /** {@inheritDoc} */
    @Override public boolean addDeploymentInfo() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!super.writeTo(buf, writer))
            return false;

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 3:
                if (!writer.writeByteArray("affAssignmentBytes", affAssignmentBytes))
                    return false;

                writer.incrementState();

            case 4:
                if (!writer.writeByteArray("affAssignmentIdsBytes", affAssignmentIdsBytes))
                    return false;

                writer.incrementState();

            case 5:
                if (!writer.writeByteArray("idealAffAssignmentBytes", idealAffAssignmentBytes))
                    return false;

                writer.incrementState();

            case 6:
                if (!writer.writeMessage("topVer", topVer))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        if (!super.readFrom(buf, reader))
            return false;

        switch (reader.state()) {
            case 3:
                affAssignmentBytes = reader.readByteArray("affAssignmentBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 4:
                affAssignmentIdsBytes = reader.readByteArray("affAssignmentIdsBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 5:
                idealAffAssignmentBytes = reader.readByteArray("idealAffAssignmentBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 6:
                topVer = reader.readMessage("topVer");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridDhtAffinityAssignmentResponse.class);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtAffinityAssignmentResponse.class, this);
    }
}
