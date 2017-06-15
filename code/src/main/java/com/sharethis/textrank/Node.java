/* Copyright (c) 2009, ShareThis, Inc. All rights reserved. Redistribution and
 * use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: Redistributions of source
 * code must retain the above copyright notice, this list of conditions and the
 * following disclaimer. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. Neither
 * the name of the ShareThis, Inc., nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.sharethis.textrank;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.math3.util.Precision;

import com.google.common.collect.ComparisonChain;

/**
 * Implements a node in the TextRank graph, denoting some noun or adjective
 * morpheme.
 *
 * @author paco@sharethis.com
 */

public class Node implements Comparable<Node> {

    /**
     * Public members.
     */

    public Set<Node> edges = new HashSet<>();
    public double rank = 0.0D;
    public String key = null;
    public boolean marked = false;
    public NodeValue value = null;

    /**
     * Private constructor.
     */

    private Node(final String key, final NodeValue value) {
        this.rank = 1.0D;
        this.key = key;
        this.value = value;
    }

    /**
     * Compare method for sort ordering.
     */

    @Override
    public int compareTo(final Node that) {
        return ComparisonChain.start().compare(rank, that.rank).compare(value.text, that.value.text).result();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Node)) {
            return false;
        }
        Node other = (Node) obj;
        return rank == other.rank && Objects.equals(value.text, other.value.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, value.text);
    }

    /**
     * Connect two nodes with a bi-directional arc in the graph.
     */

    public void connect(final Node that) {
        this.edges.add(that);
        that.edges.add(this);
    }

    /**
     * Disconnect two nodes removing their bi-directional arc in the graph.
     */

    public void disconnect(final Node that) {
        this.edges.remove(that);
        that.edges.remove(this);
    }

    /**
     * Create a unique identifier for this node, returned as a hex string.
     */

    public String getId() {
        return Integer.toString(hashCode(), 16);
    }

    /**
     * Factory method.
     */

    public static Node buildNode(final Graph graph, final String key, final NodeValue value)

    {
        Node n = graph.get(key);

        if (n == null) {
            n = new Node(key, value);
            graph.put(key, n);
        }

        return n;
    }

    /**
     * Search nearest neighbors in WordNet subgraph to find the maximum rank of
     * any adjacent SYNONYM synset.
     */

    public double maxNeighbor(final double min, final double coeff) {
        double adjusted_rank = 0.0D;

        if (edges.size() > 1) {
            // consider the immediately adjacent synsets

            double max_rank = 0.0D;

            if (max_rank > 0.0D) {
                // adjust it for scale [0.0, 1.0]
                adjusted_rank = (max_rank - min) / coeff;
            }
        } else {
            // consider the synsets of the one component keyword

            for (Node n : edges) {
                if (n.value instanceof KeyWord) {
                    // there will only be one
                    adjusted_rank = n.maxNeighbor(min, coeff);
                }
            }
        }

        return adjusted_rank;
    }

    /**
     * Traverse the graph, serializing out the nodes and edges.
     */

    public void serializeGraph(final Set<String> entries) {
        StringBuilder sb = new StringBuilder();

        // emit text and ranks vector

        marked = true;

        sb.append("node").append('\t');
        sb.append(getId()).append('\t');
        sb.append(value.getDescription()).append('\t');
        sb.append(Precision.round(rank, 3));
        entries.add(sb.toString());

        // emit edges

        for (Node n : edges) {
            sb = new StringBuilder();
            sb.append("edge").append('\t');
            sb.append(getId()).append('\t');
            sb.append(n.getId());
            entries.add(sb.toString());

            if (!n.marked) {
                // tail recursion on child
                n.serializeGraph(entries);
            }
        }
    }
}
