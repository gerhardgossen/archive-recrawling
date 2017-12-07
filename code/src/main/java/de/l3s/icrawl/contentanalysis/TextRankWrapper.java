package de.l3s.icrawl.contentanalysis;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.sharethis.textrank.MetricVector;
import com.sharethis.textrank.TextRank;

/**
 * Class wrapping an implementation of the TextRank algorithm for extracting
 * keywords of a text. Usage: call method rank() and retrieve list of keywords.
 */
public class TextRankWrapper {
    private final TextRank englishTextRank;
    private final TextRank germanTextRank;

    public static enum LANG_CODE {
        DE("de"), EN("en");

        private String lang;

        private LANG_CODE(String s) {
            this.lang = s;
        }

        public String getLangCode() {
            return this.lang;
        }
    }

    public TextRankWrapper() {
        this.englishTextRank = new TextRank("res", "en");
        this.germanTextRank = new TextRank("res", "de");
    }

    /**
     * get the top k keywords of a text by using the <a
     * href="http://www.aclweb.org/anthology/W04-3252">TextRank</a> algorithm
     *
     * @param text
     *            the text where keywords should be found
     * @param lang
     *            the language of the text
     * @param k
     *            the number of keywords to be returned
     * @return a array of at most k keywords, if there are less keywords found
     *         array contains less keywords (at least no keyword)
     */
    public List<String> rank(String text, Locale lang, int k) {
        Preconditions.checkArgument(k >= 0, "k must be greater or equal 0, but is %s", k);
        Preconditions.checkNotNull(text, "Text must not be null");

        TextRank ranker;
        switch (lang.getLanguage()) {
        case "en":
            ranker = this.englishTextRank;
            break;
        case "de":
            ranker = this.germanTextRank;
            break;
        default:
            ranker = this.englishTextRank;
            break;
        }
        ranker.prepCall(text, false);

        Collection<MetricVector> col = ranker.call();

        List<String> ret = Lists.newArrayListWithExpectedSize(k);
        for (MetricVector mv : Ordering.natural().greatestOf(col, k)) {
            ret.add(mv.value.text);
        }
        return ret;
    }
}