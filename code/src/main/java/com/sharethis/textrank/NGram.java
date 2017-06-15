/*
Copyright (c) 2009, ShareThis, Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the name of the ShareThis, Inc., nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.sharethis.textrank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

/**
 * Implements a wrapper for tallying an n-gram in the text contains of
 * selected keywords.
 *
 * @author paco@sharethis.com
 */

public class
    NGram
    extends NodeValue
    implements Comparable<NGram>
{

    /**
     * Public members.
     */

    public Set<Node> nodes = new HashSet<Node>();
    public Set<Context> contexts = new HashSet<Context>();
    public int length = 0;


    /**
     * Private constructor.
     */

    private
	NGram (final String text, final Set<Node> nodes, final Context context)
    {
	this.text = text;
	this.nodes = nodes;
	this.length = nodes.size();
	this.contexts.add(context);
    }


    /**
     * Determine the count, i.e., how often this n-gram recurs within
     * the text.
     */

    public int
	getCount ()
    {
	return contexts.size();
    }


    /**
     * Create a description text for this value.
     */

    @Override
    public String
	getDescription ()
    {
	return "NGRAM" + '\t' + getCollocation();
    }


    /**
     * Short-cut replacement for a toString() method.
     */

    public String
	renderContexts ()
    {
	final StringBuilder sb = new StringBuilder();

	for (Context c : contexts) {
	    sb.append("\n  ").append(c.start).append(": ").append(c.s.text);
	}

	return sb.toString();
    }


    /**
     * Compare method for sort ordering.
     */

    @Override
    public int
	compareTo (final NGram that)
 {
        return ComparisonChain.start()
            .compare(length, that.length)
            .compare(getCount(), that.getCount())
            .compare(text, that.text)
            .result();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NGram)) {
            return false;
        }
        NGram other = (NGram) obj;
        return length == other.length && getCount() == other.getCount()
                && Objects.equal(text, other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(length, getCount(), text);
    }

    /**
     * Factory method.
     */

    public static NGram
 buildNGram(final Graph ngrams, final Sentence s,
            final List<Integer> token_span, final double max_rank)

    {
        final Set<Node> nodes = new HashSet<Node>();
        final StringBuilder sb_key = new StringBuilder("NGram");
        final StringBuilder sb_text = new StringBuilder();

	for (int i : token_span) {
	    if (!"".equals(s.token_list[i])) {
		nodes.add(s.node_list[i]);
		sb_key.append(s.node_list[i].key);
		sb_text.append(s.token_list[i]).append(' ');
	    }
	}

	final Context context = new Context(s, token_span.get(0));
	final String gram_key = sb_key.toString();

	Node n = ngrams.get(gram_key);
	NGram gram = null;

	if (n == null) {
	    gram = new NGram(sb_text.toString().trim(), nodes, context);

	    if (!"".equals(gram.text.trim())) {
		n = Node.buildNode(ngrams, gram_key, gram);
		n.rank = max_rank;

		ngrams.put(gram_key, n);
	    }
	}
	else {
	    gram = (NGram) n.value;
	    gram.contexts.add(context);
	}

	return gram;
    }


    /**
     * Report the n-grams marked in each sentence.
     */

    public static Graph
	collectNGrams (final LanguageModel lang, final List<Sentence> s_list, final double rank_threshold)

    {
	final Graph ngrams = new Graph();
        final List<Integer> token_span = new ArrayList<Integer>();

	for (Sentence s : s_list) {
	    boolean span_marked = false;
	    double max_rank = 0.0D;

	    token_span.clear();

	    for (int i = 0; i < s.node_list.length; i++) {
		if (s.node_list[i] == null) {
		    // evaluate the accumulated token span, after
		    // reaching a phrase boundary

                    if (span_marked
                            && !token_span.isEmpty()
                            && ((token_span.size() > 1) || ((max_rank >= rank_threshold) && lang.isNoun(((KeyWord) s.node_list[token_span.get(0)].value).pos)))) {
                        buildNGram(ngrams, s, token_span, max_rank);
                    }

		    // reset the span

		    token_span.clear();
		    span_marked = false;
		    max_rank = 0.0D;
		}
		else {
		    // keep widening the token span
		    token_span.add(i);
		    span_marked = span_marked || s.node_list[i].marked;
		    max_rank = Math.max(max_rank, s.node_list[i].rank);
		}
	    }
	}

	return ngrams;
    }


    /**
     * Determine a statistical distribution for the n-gram subgraph.
     */

    public static int
	calcStats (final Graph subgraph)
    {
	int ngram_max_count = 2;
	subgraph.dist_stats.clear();

	for (Node n : subgraph.values()) {
	    final NGram gram = (NGram) n.value;

	    subgraph.dist_stats.addValue(n.rank);
	    ngram_max_count = Math.max(gram.getCount(), ngram_max_count);
	}

	return ngram_max_count;
    }
}
