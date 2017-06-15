package com.sharethis.textrank;

import java.util.ArrayList;
import java.util.List;

import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.GermanStemmer;

import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.util.CoreMap;

/**
 * Implementation of English-specific tools for natural language
 * processing.
 */
public class CoreNlpLanguageModel extends LanguageModel {

    private static final String GERMAN_TAGGER_MODEL = "models/german-fast.tagger";
    private static final String ENGLISH_TAGGER_MODEL = "models/english-left3words-distsim.tagger";
    private final WordsToSentencesAnnotator splitter;
    private final TokenizerAnnotator tokenizer;
    private final POSTaggerAnnotator tagger;
    private final SnowballProgram stemmer;


    public CoreNlpLanguageModel(WordsToSentencesAnnotator splitter, TokenizerAnnotator tokenizer,
            POSTaggerAnnotator tagger, SnowballProgram stemmer) {
        this.splitter = splitter;
        this.tokenizer = tokenizer;
        this.tagger = tagger;
        this.stemmer = stemmer;
    }

    public static LanguageModel englishModel() {
        return new CoreNlpLanguageModel(new WordsToSentencesAnnotator(false),
            new TokenizerAnnotator(false), new POSTaggerAnnotator(ENGLISH_TAGGER_MODEL, true),
            new EnglishStemmer());
	}

    public static LanguageModel germanModel(){
        return new CoreNlpLanguageModel(new WordsToSentencesAnnotator(false),
            new TokenizerAnnotator(false), new POSTaggerAnnotator(GERMAN_TAGGER_MODEL, false),
            new GermanStemmer());

	}

	@Override
	public String[] splitParagraph(String text) {
		Annotation document = new Annotation(text);
		tokenizer.annotate(document);
		splitter.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		return sentences.stream().map(CoreMap::toString).filter(s -> s != null).toArray(String[]::new);
	}

    String[] tokenizeSentence(String text) {
		Annotation document = new Annotation(text);
		tokenizer.annotate(document);
		List<String> tokenList = new ArrayList<String>();
		for (CoreLabel token : document.get(TokensAnnotation.class)) {
			tokenList.add(token.get(TextAnnotation.class));
		}
		return tokenList.toArray(new String[1]);
	}

	@Override
    public Sentence parseSentence(String text) {
		Annotation document = new Annotation(text);
		tokenizer.annotate(document);
		splitter.annotate(document);
		tagger.annotate(document);
        List<String> tokens = new ArrayList<>();
        List<String> tags = new ArrayList<>();
		for (CoreLabel token : document.get(TokensAnnotation.class)) {
            tokens.add(token.get(TextAnnotation.class));
            tags.add(token.get(PartOfSpeechAnnotation.class));
		}
        return new Sentence(text, tokens.toArray(new String[tokens.size()]),
            tags.toArray(new String[tags.size()]));
	}

	@Override
	public String getNodeKey(String text, String pos)  {
		return pos.substring(0, 2) + stemToken(scrubToken(text)).toLowerCase();
	}

	@Override
	public boolean isNoun(String pos) {
		return pos.startsWith("NN");
	}

	@Override
	public boolean isAdjective(String pos) {
		return pos.startsWith("JJ");
	}

	@Override
	public String stemToken(String token) {
		stemmer.setCurrent(token);
		stemmer.stem();

		return stemmer.getCurrent();
	}
}
