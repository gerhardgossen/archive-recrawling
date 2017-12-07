package net.sourceforge.jwbf.mapper;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;

import net.sourceforge.jwbf.JWBF;

public class MediawikiModule extends SimpleModule {

    private static final String ARTIFACT_ID = "jwbf";
    private static final String GROUP_ID = "net.sourceforge";

    static class MediawikiBooleanDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                if ("".equals(jp.getValueAsString())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final long serialVersionUID = 1L;

    public MediawikiModule() {
        super("Mediawiki", makeVersion());
        addDeserializer(Boolean.class, new MediawikiBooleanDeserializer());
        addDeserializer(Boolean.TYPE, new MediawikiBooleanDeserializer());
    }

    private static Version makeVersion() {
        String version = JWBF.getVersion(JWBF.class);
        if (version == JWBF.VERSION_FALLBACK_VALUE) {
            return new Version(-1, -1, -1, null, GROUP_ID, ARTIFACT_ID);
        }
        String[] parts = version.split("-", 2);
        String snapshot = parts.length == 2 ? parts[1] : null;
        String[] versionParts = parts[0].split("\\.", 3);
        int major = Integer.parseInt(versionParts[0]);
        int minor = Integer.parseInt(versionParts[1]);
        int patchLevel = Integer.parseInt(versionParts[2]);
        return new Version(major, minor, patchLevel, snapshot, GROUP_ID, ARTIFACT_ID);
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.setNamingStrategy(PropertyNamingStrategy.LOWER_CASE);
    }

}
