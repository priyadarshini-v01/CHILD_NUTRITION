package com.childnutrition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiNutritionLanguageModelTests {
    @Test
    void sendsConversationAndReturnsOnlyTrustedSourceCitations() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = """
                {"output":[{"type":"message","content":[{"type":"output_text","text":"Protein supports growth.","annotations":[
                  {"type":"url_citation","url":"https://www.who.int/news-room/fact-sheets/detail/healthy-diet","title":"WHO healthy diet"},
                  {"type":"url_citation","url":"https://example.test/fake","title":"Fake source"}
                ]}]}]}
                """;
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.input[0].role").value("user"))
                .andExpect(jsonPath("$.tools[0].filters.allowed_domains[0]").value("who.int"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        OpenAiNutritionLanguageModel model = new OpenAiNutritionLanguageModel(builder.build(), new ObjectMapper(), "test-model", true);
        NutritionLanguageModel.ModelAnswer answer = model.answer("What is protein?", List.of(), "no food match");

        assertEquals("Protein supports growth.", answer.text());
        assertEquals(1, answer.references().size());
        assertEquals("https://www.who.int/news-room/fact-sheets/detail/healthy-diet", answer.references().get(0).url());
        server.verify();
    }

    @Test
    void providerHttpFailureIsSanitized() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withTooManyRequests().body("secret provider diagnostic"));
        OpenAiNutritionLanguageModel model = new OpenAiNutritionLanguageModel(builder.build(), new ObjectMapper(), "test-model", false);

        NutritionAiUnavailableException error = assertThrows(NutritionAiUnavailableException.class,
                () -> model.answer("What is protein?", List.of(), ""));
        assertFalse(error.getMessage().contains("secret"));
        server.verify();
    }
}
