package de.l3s.gossen.contentanalysis;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import de.l3s.gossen.contentanalysis.DocumentVector;
import de.l3s.gossen.contentanalysis.LanguageModel;
import de.l3s.gossen.contentanalysis.LanguageModels;
import de.l3s.gossen.contentanalysis.LanguageModel.KeywordMatcher;
import de.l3s.gossen.util.TextExtractor;

import static de.l3s.gossen.crawler.TestUtils.loadDocument;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

public class DocumentVectorSimilarityTest {

    @Test
    public void testDocumentVectorForDocument() throws IOException {
        String text = TextExtractor.extractText(loadDocument(getClass(), "lhc.html",
            "https://de.wikipedia.org/wiki/Large_Hadron_Collider"));
        Analyzer analyzer = new GermanAnalyzer(CharArraySet.EMPTY_SET);
        KeywordMatcher matcher = KeywordMatcher.matchNone();
        Map<String, Double> idfDictionary;
        try (InputStream is = new GZIPInputStream(Resources.getResource("dictionary-DE.tsv.gz").openStream())) {
            idfDictionary = LanguageModels.readIdfDictionary(is);
        }
        LanguageModel lm = new LanguageModel(analyzer, idfDictionary);
        DocumentVector dv = lm.buildDocumentVector(text, matcher);
        assertThat(keys(dv.topComponents(10)), not(contains("wurd")));
        assertThat(lm.analyzeToken("abgerufen"), is("abgeruf"));
    }

    private List<String> keys(List<? extends Entry<String, ?>> entries) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Entry<String, ?> entry : entries) {
            builder.add(entry.getKey());
        }
        return builder.build();
    }

}
