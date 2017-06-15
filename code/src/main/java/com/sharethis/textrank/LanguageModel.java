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

/**
 * Facade for handling language-specific tools in natural language
 * processing.
 *
 * @author paco@sharethis.com
 */

public abstract class
    LanguageModel
{

    /**
     * Public definitions.
     */

    public static final int TOKEN_LENGTH_LIMIT = 50;


    /**
     * Factory method, loads libraries for StanfordCoreNLP based on the given
     * language code.
     */

    public static LanguageModel
	buildLanguage (final String lang_code)

    {
	LanguageModel lang = null;

	if ("en".equals(lang_code)) {
            lang = CoreNlpLanguageModel.englishModel();
	} else if ("de".equals(lang_code)) {
            lang = CoreNlpLanguageModel.germanModel();
	}

	return lang;
    }


    /**
     * Split sentences within the paragraph text.
     */

    public abstract String[]
	splitParagraph (final String text)
	;


    /**
     * Tokenize and tag the sentence text.
     */
    public abstract Sentence 
        parseSentence(final String text)
        ;


    /**
     * Prepare a stable key for a graph node (stemmed, lemmatized)
     * from a token.
     */

    public abstract String
	getNodeKey (final String text, final String pos)
	;


    /**
     * Determine whether the given PoS tag is relevant to add to the
     * graph.
     */

    public boolean
	isRelevant (final String pos)
    {
	return isNoun(pos) || isAdjective(pos);
    }


    /**
     * Determine whether the given PoS tag is a noun.
     */

    public abstract boolean
	isNoun (final String pos)
	;


    /**
     * Determine whether the given PoS tag is an adjective.
     */

    public abstract boolean
	isAdjective (final String pos)
	;


    /**
     * Perform stemming on the given token.
     */

    public abstract String
	stemToken (final String token)
	;


    /**
     * Clean the text for a token.
     *
     * @param token_text input text for a token.
     * @return clean text
     */

    public String
        scrubToken (final String token_text)

    {
        String scrubbed = token_text;

        if (scrubbed.length() > TOKEN_LENGTH_LIMIT) {
            scrubbed = scrubbed.substring(0, TOKEN_LENGTH_LIMIT);
        }

        return scrubbed;
    }
}
