package de.l3s.icrawl.domain.specification;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

public class NamedEntity implements Serializable {
    public static enum Type {
        PERSON, ORGANIZATION, LOCATION
    }

    /**
     * An entity label.
     *
     * The label can have an associated language code limit the validity of the
     * label to that language. For example, "München:de" and "Munich:en" are two
     * possible labels for the same entity.
     */
    public static class Label implements Serializable {
        private static final long serialVersionUID = 1L;

        @Nonnull
        private final String name;
        @Nullable
        private final Locale language;

        /**
         * Create a new label with name and language
         *
         * @param name
         *            the textual name of the label (not null)
         * @param language
         *            the 2-letter language code (null allowed)
         */
        @JsonCreator
        public Label(@Nonnull @JsonProperty("name") String name, @Nullable @JsonProperty("language") Locale language) {
            this.name = Objects.requireNonNull(name);
            this.language = language;
        }

        /**
         * Get the name of the label
         *
         * @return the textual name of the label (not null)
         */
        public String getName() {
            return name;
        }

        /**
         * Get the language of the label.
         *
         * @return the 2-letter language code or null if the language is not
         *         specified
         */
        public Locale getLanguage() {
            return language;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, language);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Label other = (Label) obj;
            return Objects.equals(language, other.language) && Objects.equals(name, other.name);
        }

        @Override
        public String toString() {
            return String.format("%s:%s", name, language);
        }

    }

    private static final long serialVersionUID = 1L;

    private final Type type;
    private final Set<Label> labels;

    public NamedEntity(Type type, String label) {
        this.type = type;
        this.labels = new HashSet<>();
        labels.add(new Label(label, null));
    }

    @JsonCreator
    public NamedEntity(@JsonProperty("type") Type type, @JsonProperty("labels") Set<Label> labels) {
        this.type = type;
        this.labels = labels;
    }

    public NamedEntity(Type type, Label... labels) {
        this.type = type;
        this.labels = Sets.newHashSet(labels);
    }

    public Type getType() {
        return type;
    }

    public Set<Label> getLabels() {
        return labels;
    }

    public void addLabel(Label label) {
        labels.add(label);
    }

    public void addLabel(String name, Locale language) {
        labels.add(new Label(name, language));
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, labels);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        NamedEntity other = (NamedEntity) obj;
        return Objects.equals(type, other.type) && Objects.equals(labels, other.labels);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", type, labels);
    }

}
