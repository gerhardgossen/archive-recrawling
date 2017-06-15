package de.l3s.gossen.contentanalysis;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import de.l3s.gossen.domain.specification.NamedEntity;
import de.l3s.gossen.domain.specification.NamedEntity.Type;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Triple;

public class Labeler {
    private static final Logger logger = LoggerFactory.getLogger(Labeler.class);
    private final CRFClassifier<CoreMap> classifier;
    private final String personTag;
    private final String organisationTag;
    private final String locationTag;
    private final Locale language;

    public Labeler(CRFClassifier<CoreMap> classifier, Locale language,
            String personTag, String organisationTag, String locationTag) {
        this.classifier = classifier;
        this.language = language;
        this.personTag = personTag;
        this.organisationTag = organisationTag;
        this.locationTag = locationTag;
    }

    @Nullable
    protected NamedEntity.Type map(String annotationType) {
        if (personTag.equals(annotationType)) {
            return Type.PERSON;
        } else if (organisationTag.equals(annotationType)) {
            return Type.ORGANIZATION;
        } else if (locationTag.equals(annotationType)) {
            return Type.LOCATION;
        } else {
            logger.trace("Unknown annotation type {}", annotationType);
            return null;
        }
    }

    public Collection<NamedEntity> extractEntities(String text) {
        List<Triple<String, Integer, Integer>> annotations = classifier.classifyToCharacterOffsets(text);
        List<NamedEntity> entities = Lists.newArrayListWithExpectedSize(annotations.size());
        for (Triple<String, Integer, Integer> el : annotations) {
            Type type = map(el.first);
            if (type != null) {
                NamedEntity.Label label = new NamedEntity.Label(label(text, el), language);
                entities.add(new NamedEntity(type, label));
            }
        }
        return entities;
    }

    private static String label(String text, Triple<String, Integer, Integer> annotation) {
        return text.substring(annotation.second, annotation.third)
            .replaceAll("[^-\\w\\d_]+", " ")
            .trim();
    }

}
