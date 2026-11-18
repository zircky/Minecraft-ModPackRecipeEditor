package dev.recipeeditor.registry;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
public final class ResourceJsonReader {
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(2*1024*1024).build()).build());
    public JsonNode read(final String raw) throws IOException {
        return mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(raw.startsWith("\uFEFF")?raw.substring(1):raw);
    }
}
