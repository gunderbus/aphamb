package com.cole.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaClient {
    private final HttpClient httpClient;
    private final Gson gson;

    public OllamaClient() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        gson = new Gson();
    }

    public String generate(String baseUrl, String model, String prompt) throws IOException, InterruptedException {
        var requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);

        var normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        var response = httpClient.send(
            HttpRequest.newBuilder(URI.create(normalizedBaseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 400) {
            throw new IOException("Ollama error (" + response.statusCode() + "): " + response.body());
        }

        var json = gson.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("response")) {
            throw new IOException("Ollama returned an unexpected response: " + response.body());
        }

        return json.get("response").getAsString().trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:11434";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
