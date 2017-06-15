package de.l3s.gossen.contentanalysis;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.openimaj.text.nlp.language.LanguageDetector.WeightedLocale;

import edu.stanford.nlp.ie.crf.CRFClassifier;

public class LabelerFactory {
    private final Map<String, Labeler> labelers;
    private final String defaultLabeler;
    private final Function<String, Labeler> provider;

    public LabelerFactory(Map<String, Labeler> labelers, String defaultLabelerLanguage,
            Function<String, Labeler> provider) {
        this.labelers = labelers;
        this.defaultLabeler = defaultLabelerLanguage;
        this.provider = provider;
    }

    public static LabelerFactory defaultFactory() {

        Function<String, Labeler> provider = new Function<String, Labeler>() {
            @Override
            public Labeler apply(String language) {
                if ("en".equals(language)) {
                    return new Labeler(
                        CRFClassifier.getClassifierNoExceptions("english.all.3class.distsim.crf.ser.gz"),
                        Locale.ENGLISH, "PERSON", "ORGANIZATION", "LOCATION");
                } else if ("de".equals(language)) {
                    return new Labeler(
                        CRFClassifier.getClassifierNoExceptions("dewac_175m_600.crf.ser.gz"), Locale.GERMAN,
                        "I-PER", "I-ORG", "I-LOC");
                } else {
                    return null;
                }
            }
        };

        return new LabelerFactory(new HashMap<String, Labeler>(), "en", provider);
    }

    public Labeler get(WeightedLocale locale) {
        Labeler labeler = getInternal(locale.language);
        return labeler != null ? labeler : getInternal(defaultLabeler);
    }

    private Labeler getInternal(String language) {
        synchronized (labelers) {
            Labeler labeler = labelers.get(language);
            if (labeler == null && provider != null) {
                labeler = provider.apply(language);
                if (labeler != null) {
                    labelers.put(language, labeler);
                }
            }
            return labeler;
        }
    }
}
