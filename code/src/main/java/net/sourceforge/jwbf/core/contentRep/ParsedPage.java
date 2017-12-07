package net.sourceforge.jwbf.core.contentRep;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.collect.ImmutableMap;

public class ParsedPage {
    public static class Redirect {
        @JsonProperty
        public String from;
        @JsonProperty
        public String to;
        @JsonProperty
        public String tofragment;

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public String getToFragment() {
            return tofragment;
        }
    }

    static class ParsedText {
        @JsonProperty("*")
        String text;

        public String getText() {
            return text;
        }
    }

    public static class InterWikiLink {
        @JsonProperty
        String prefix;
        @JsonProperty
        String url;
        @JsonProperty("*")
        String name;

        public String getPrefix() {
            return prefix;
        }

        public String getUrl() {
            return url;
        }

        public String getName() {
            return name;
        }

    }

    public static class Section {
        @JsonProperty
        int tocLevel;
        @JsonProperty
        int level;
        @JsonProperty
        String line;
        @JsonProperty
        String number;
        @JsonProperty
        int index;
        @JsonProperty
        String fromtitle;
        @JsonProperty
        long byteOffset;
        @JsonProperty
        String anchor;

        public int getTocLevel() {
            return tocLevel;
        }

        public int getLevel() {
            return level;
        }

        public String getLine() {
            return line;
        }

        public String getNumber() {
            return number;
        }

        public int getIndex() {
            return index;
        }

        public String getFromtitle() {
            return fromtitle;
        }

        public long getByteOffset() {
            return byteOffset;
        }

        public String getAnchor() {
            return anchor;
        }
    }

    public static class Link {
        @JsonProperty("ns")
        int namespace;
        @JsonProperty("*")
        String name;
        @JsonProperty
        boolean exists;

        public int getNamespace() {
            return namespace;
        }

        public String getName() {
            return name;
        }

        public boolean exists() {
            return exists;
        }

    }

    public static class Category {
        private String sortkey;
        @JsonProperty("*")
        private String name;
        private boolean hidden;

        public String getSortkey() {
            return sortkey;
        }

        public String getName() {
            return name;
        }

        public boolean isHidden() {
            return hidden;
        }

        void setSortkey(String sortkey) {
            this.sortkey = sortkey;
        }

        void setName(String name) {
            this.name = name;
        }

        void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

    }

    @JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
    public static class LangLink {
        private Locale lang;
        private String url;
        private String langName;
        private String autonym;
        @JsonProperty("*")
        private String pageName;

        public Locale getLang() {
            return lang;
        }

        public String getUrl() {
            return url;
        }

        public String getLangName() {
            return langName;
        }

        public String getAutonym() {
            return autonym;
        }

        public String getPageName() {
            return pageName;
        }

        void setLang(Locale lang) {
            this.lang = lang;
        }

        void setUrl(String url) {
            this.url = url;
        }

        void setLangName(String langName) {
            this.langName = langName;
        }

        void setAutonym(String autonym) {
            this.autonym = autonym;
        }

        void setPageName(String pageName) {
            this.pageName = pageName;
        }

    }

    private String title;
    private String pageId;
    private long revId;
    private String text;
    private List<LangLink> langLinks;
    private List<Category> categories;
    private List<Link> links;
    private List<Link> templates;
    private List<String> images;
    private List<String> externalLinks;
    private List<Section> sections;
    private String displayTitle;
    @JsonProperty("iwlinks")
    private Collection<InterWikiLink> interWikiLinks;
    private Map<String, String> properties;
    private List<Redirect> redirects;

    public String getTitle() {
        return title;
    }

    public String getPageId() {
        return pageId;
    }

    public long getRevId() {
        return revId;
    }

    public String getText() {
        return text;
    }

    public List<LangLink> getLangLinks() {
        return langLinks;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public List<Link> getLinks() {
        return links;
    }

    public List<Link> getTemplates() {
        return templates;
    }

    public List<String> getImages() {
        return images;
    }

    public List<String> getExternalLinks() {
        return externalLinks;
    }

    public List<Section> getSections() {
        return sections;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public Collection<InterWikiLink> getInterWikiLinks() {
        return interWikiLinks;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public List<Redirect> getRedirects() {
        return redirects;
    }

    void setTitle(String title) {
        this.title = title;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    void setRevId(long revId) {
        this.revId = revId;
    }

    @JsonProperty
    void setText(ParsedText text) {
        this.text = text.text;
    }

    void setLangLinks(List<LangLink> langLinks) {
        this.langLinks = langLinks;
    }

    void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    void setLinks(List<Link> links) {
        this.links = links;
    }

    void setTemplates(List<Link> templates) {
        this.templates = templates;
    }

    void setImages(List<String> images) {
        this.images = images;
    }

    void setExternalLinks(List<String> externalLinks) {
        this.externalLinks = externalLinks;
    }

    void setSections(List<Section> sections) {
        this.sections = sections;
    }

    void setDisplayTitle(String displayTitle) {
        this.displayTitle = displayTitle;
    }

    void setInterWikiLinks(Collection<InterWikiLink> interWikiLinks) {
        this.interWikiLinks = interWikiLinks;
    }

    @JsonProperty
    void setProperties(List<Map<String, String>> properties) {
        this.properties = convertProperties(properties);
    }

    private static Map<String, String> convertProperties(List<Map<String, String>> properties) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Map<String, String> kv : properties) {
            builder.put(kv.get("name"), kv.get("*"));
        }
        return builder.build();
    }

    public void setRedirects(List<Redirect> redirects) {
        this.redirects = redirects;
    }
}
