package io.dataguardians.automation.datagen.datasynth;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.dataguardians.exceptions.HttpException;
import io.dataguardians.model.openai.Response;
import io.dataguardians.openai.GenerativeAPI;
import io.dataguardians.openai.endpoints.ChatApiEndpointRequest;
import io.dataguardians.security.TokenProvider;

public class ShortTextGenerator extends DataGenerator<String> {
    public ShortTextGenerator(TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config) {
        super(token, generator, config);
    }

    protected String generateInput() {
        return "Write me a random short paragraph.";
    }

    public String generate() throws HttpException, JsonProcessingException {
        ChatApiEndpointRequest request = ChatApiEndpointRequest.builder().input(generateInput()).maxTokens(1024)
                .build();
        request.setMaxTokens(config.getMaxTokens());
        Response hello = api.sample(request, Response.class);
        return hello.concatenateResponses();
    }

}
