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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;


/**
 * Java implementation of the TextRank algorithm by Rada Mihalcea, et al.
 *    http://lit.csci.unt.edu/index.php/Graph-based_NLP
 *
 * @author paco@sharethis.com
 */

public class
    TextRank
    implements Callable<Collection<MetricVector>>
{

    /**
     * Public definitions.
     */

    public static final String NLP_RESOURCES = "nlp.resources";
    public static final double MIN_NORMALIZED_RANK = 0.05D;
    public static final int MAX_NGRAM_LENGTH = 5;
    public static final long MAX_WORDNET_TEXT = 2000L;
    public static final long MAX_WORDNET_GRAPH = 600L;


    /**
     * Protected members.
     */

    protected final LanguageModel lang;

    protected String text = null;
    protected boolean use_wordnet = false;

    protected Graph graph = null;
    protected Graph ngram_subgraph = null;
    protected Map<NGram, MetricVector> metric_space = null;

    protected long start_time = 0L;
    protected long elapsed_time = 0L;


    /**
     * Constructor.
     */

    public
	TextRank (final String res_path, final String lang_code)

    {
	lang = LanguageModel.buildLanguage(lang_code);
    }


    /**
     * Prepare to call algorithm with a new text to analyze.
     */

    public void
	prepCall (final String text, final boolean use_wordnet)
    {
	graph = new Graph();
	ngram_subgraph = null;
	metric_space = new HashMap<NGram, MetricVector>();

	this.text = text;
	this.use_wordnet = use_wordnet;
    }


    /**
     * Run the TextRank algorithm on the given semi-structured text
     * (e.g., results of parsed HTML from crawled web content) to
     * build a graph of weighted key phrases.
     */

    @Override
    public Collection<MetricVector>
	call ()
    {
	//////////////////////////////////////////////////
	// PASS 1: construct a graph from PoS tags

	// scan sentences to construct a graph of relevent morphemes

        final List<Sentence> s_list = new ArrayList<Sentence>();

	for (String sent_text : lang.splitParagraph(text)) {
            final Sentence s = lang.parseSentence(sent_text.trim());
            s.mapTokens(lang, graph);
	    s_list.add(s);
	}

	//////////////////////////////////////////////////
	// PASS 2: run TextRank to determine keywords

	final int max_results =
	    (int) Math.round(graph.size() * Graph.KEYWORD_REDUCTION_FACTOR);

	graph.runTextRank();
	graph.sortResults(max_results);

	ngram_subgraph = NGram.collectNGrams(lang, s_list, graph.getRankThreshold());

	//////////////////////////////////////////////////
	// PASS 3: lemmatize selected keywords and phrases

	Graph synset_subgraph = new Graph();

	// augment the graph with n-grams added as nodes

	for (Node n : ngram_subgraph.values()) {
	    final NGram gram = (NGram) n.value;

	    if (gram.length < MAX_NGRAM_LENGTH) {
		graph.put(n.key, n);

		for (Node keyword_node : gram.nodes) {
		    n.connect(keyword_node);
		}
	    }
	}

	//////////////////////////////////////////////////
	// PASS 4: re-run TextRank on the augmented graph

	graph.runTextRank();

	// collect stats for metrics

	final int ngram_max_count =
	    NGram.calcStats(ngram_subgraph);

	//////////////////////////////////////////////////
	// PASS 5: construct a metric space for overall ranking

	final double link_min = ngram_subgraph.dist_stats.getMin();
	final double link_coeff = ngram_subgraph.dist_stats.getMax() - ngram_subgraph.dist_stats.getMin();

	final double count_min = 1;
	final double count_coeff = (double) ngram_max_count - 1;

	final double synset_min = synset_subgraph.dist_stats.getMin();
	final double synset_coeff = synset_subgraph.dist_stats.getMax() - synset_subgraph.dist_stats.getMin();

	for (Node n : ngram_subgraph.values()) {
	    final NGram gram = (NGram) n.value;

	    if (gram.length < MAX_NGRAM_LENGTH) {
		final double link_rank = (n.rank - link_min) / link_coeff;
		final double count_rank = (gram.getCount() - count_min) / count_coeff;
		final double synset_rank = use_wordnet ? n.maxNeighbor(synset_min, synset_coeff) : 0.0D;

		final MetricVector mv = new MetricVector(gram, link_rank, count_rank, synset_rank);
		metric_space.put(gram, mv);
	    }
	}

	// return results

	return metric_space.values();
    }


    //////////////////////////////////////////////////////////////////////
    // access and utility methods
    //////////////////////////////////////////////////////////////////////

    /**
     * Accessor for the graph.
     */

    public Graph
	getGraph ()
    {
	return graph;
    }

    /**
     * Serialize resulting graph to a string.
     */

    @Override
    public String
	toString ()
    {
        final Set<MetricVector> key_phrase_list = new TreeSet<MetricVector>(metric_space.values());
	final StringBuilder sb = new StringBuilder();

	for (MetricVector mv : key_phrase_list) {
	    if (mv.metric >= MIN_NORMALIZED_RANK) {
		sb.append(mv.render()).append("\t").append(mv.value.text).append("\n");
	    }
	}

	return sb.toString();
    }

}
