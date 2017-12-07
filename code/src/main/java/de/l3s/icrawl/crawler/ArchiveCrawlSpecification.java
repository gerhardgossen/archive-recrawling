package de.l3s.icrawl.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.l3s.icrawl.contentanalysis.DocumentVector;

public class ArchiveCrawlSpecification {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModules(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final String name;
    private final List<String> seedUrls;
    private final List<String> referenceDocuments;
    private final TimeSpecification referenceTime;
    private final Map<Locale, DocumentVector> referenceVectors;
    private final String description;
    private final Map<Locale, Set<String>> keywords;
    private final Locale defaultLanguage;
    private final Map<Locale, Double> correctionFactors;

    @JsonCreator
    public ArchiveCrawlSpecification(@JsonProperty("name") String name, @JsonProperty("seedUrls") List<String> seedUrls,
            @JsonProperty("referenceDocuments") List<String> referenceDocuments,
            @JsonProperty("referenceTime") TimeSpecification referenceTime,
            @JsonProperty("referenceVectors") Map<Locale, DocumentVector> referenceVectors,
            @JsonProperty("keywords") Map<Locale, Set<String>> keywords, @JsonProperty("description") String description,
            @JsonProperty("defaultLanguage") Locale defaultLanguage,
            @JsonProperty("correctionFactors") Map<Locale, Double> correctionFactors) {
        this.name = name;
        this.seedUrls = seedUrls;
        this.referenceDocuments = referenceDocuments;
        this.referenceTime = referenceTime;
        this.defaultLanguage = defaultLanguage;
        this.correctionFactors = correctionFactors;
        this.referenceVectors = Objects.requireNonNull(referenceVectors);
        this.keywords = keywords;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public List<String> getSeedUrls() {
        return seedUrls;
    }

    public List<String> getReferenceDocuments() {
        return referenceDocuments;
    }

    public TimeSpecification getReferenceTime() {
        return referenceTime;
    }

    public Map<Locale, DocumentVector> getReferenceVectors() {
        return Collections.unmodifiableMap(referenceVectors);
    }

    public String getDescription() {
        return description;
    }

    public Map<Locale, Double> getCorrectionFactors() {
        return correctionFactors;
    }

    public static ArchiveCrawlSpecification readFile(File specFile) throws IOException {
        return MAPPER.readValue(specFile, ArchiveCrawlSpecification.class);
    }

    public void writeFile(File specFile) throws IOException {
        MAPPER.writeValue(specFile, this);
    }

    public Map<Locale, Set<String>> getKeywords() {
        return keywords;
    }

    public Locale getDefaultLanguage() {
        return defaultLanguage;
    }

    @Override
    public int hashCode() {
        return Objects
            .hash(name, seedUrls, referenceDocuments, referenceTime, keywords, defaultLanguage, description, correctionFactors);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ArchiveCrawlSpecification)) {
            return false;
        }
        ArchiveCrawlSpecification o = (ArchiveCrawlSpecification) obj;
        return Objects.equals(name, o.name) && Objects.equals(seedUrls, o.seedUrls)
                && Objects.equals(referenceDocuments, o.referenceDocuments) && Objects.equals(referenceTime, o.referenceTime)
                && Objects.equals(keywords, o.keywords) && Objects.equals(defaultLanguage, o.defaultLanguage)
                && Objects.equals(description, o.description) && Objects.equals(correctionFactors, o.correctionFactors);
    }

    public ArchiveCrawlSpecification withSeedUrls(List<String> newSeedUrls) {
        return new ArchiveCrawlSpecification(name, newSeedUrls, referenceDocuments, referenceTime, referenceVectors, keywords,
            description, defaultLanguage, correctionFactors);
    }

}
