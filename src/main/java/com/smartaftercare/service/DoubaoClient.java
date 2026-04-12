package com.smartaftercare.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 豆包大模型客户端（火山方舟 API）
 * <p>
 * 替代 Go 的 pkg/doubao/client.go
 */
@Slf4j
public class DoubaoClient {

    private static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final long DEFAULT_TIMEOUT = 60;
    private static final long EMBEDDING_TIMEOUT = 10;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String chatModel;
    private final String baseUrl;
    private final OkHttpClient httpClient;

    public DoubaoClient(String apiKey, String chatModel) {
        this(apiKey, chatModel, DEFAULT_BASE_URL);
    }

    public DoubaoClient(String apiKey, String chatModel, String baseUrl) {
        if (!chatModel.startsWith("ep-")) {
            log.warn("Chat模型名称 '{}' 不是Endpoint ID格式（ep-xxx），火山方舟API可能返回404错误。" +
                    "请在火山方舟控制台创建推理接入点后，将Endpoint ID配置到DOUBAO_CHAT_MODEL环境变量中", chatModel);
        }
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    // ==================== Chat 相关 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatMessage {
        private String role;
        private String content;
        @JsonProperty("reasoning_content")
        private String reasoningContent;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class ChatRequest {
        private String model;
        private List<ChatMessage> messages;
        private double temperature;
        @JsonProperty("max_tokens")
        private int maxTokens;
        private boolean stream;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatChoice {
        private int index;
        private ChatMessage message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        private String id;
        private List<ChatChoice> choices;
    }

    /**
     * 调用大模型生成回答
     */
    public String generate(String prompt, double temperature, int maxTokens) throws IOException {
        ChatRequest req = new ChatRequest();
        req.setModel(chatModel);
        req.setMessages(List.of(
                new ChatMessage("system", "你是一个专业的家电售后服务助手，根据用户提供的家电说明书内容，准确、专业地回答用户关于家电操作、故障排查、保养维护等问题。"),
                new ChatMessage("user", prompt)
        ));
        req.setTemperature(temperature);
        req.setMaxTokens(maxTokens);

        String body = objectMapper.writeValueAsString(req);

        Request httpReq = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(httpReq).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("API返回错误(HTTP " + response.code() + "): " + respBody);
            }

            ChatResponse chatResp = objectMapper.readValue(respBody, ChatResponse.class);
            if (chatResp.getChoices() == null || chatResp.getChoices().isEmpty()) {
                throw new IOException("大模型未返回有效回答");
            }

            return chatResp.getChoices().get(0).getMessage().getContent();
        }
    }

    // ==================== Embedding 相关 ====================

    @Data
    public static class EmbeddingRequest {
        private String model;
        private List<String> input;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingData {
        private int index;
        private List<Float> embedding;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingResponse {
        private List<EmbeddingData> data;
    }

    /**
     * 批量生成文本向量（静态方法）
     */
    public static List<List<Float>> generateEmbeddings(String apiKey, String embeddingModel, List<String> texts) {
        if (!embeddingModel.startsWith("ep-")) {
            throw new IllegalArgumentException(
                    String.format("Embedding模型名称格式错误: '%s'，火山方舟API要求使用推理接入点Endpoint ID（格式: ep-xxxxxxxxxx-yyyy）", embeddingModel));
        }
        return generateEmbeddingsWithUrl(DEFAULT_BASE_URL, apiKey, embeddingModel, texts);
    }

    /**
     * 生成单条文本向量（静态方法）
     */
    public static List<Float> generateEmbedding(String apiKey, String embeddingModel, String text) {
        List<List<Float>> vectors = generateEmbeddings(apiKey, embeddingModel, List.of(text));
        if (vectors.isEmpty()) {
            throw new RuntimeException("向量化结果为空");
        }
        return vectors.get(0);
    }

    private static List<List<Float>> generateEmbeddingsWithUrl(String baseUrl, String apiKey, String embeddingModel, List<String> texts) {
        try {
            EmbeddingRequest req = new EmbeddingRequest();
            req.setModel(embeddingModel);
            req.setInput(texts);

            String body = objectMapper.writeValueAsString(req);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(EMBEDDING_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(EMBEDDING_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(EMBEDDING_TIMEOUT, TimeUnit.SECONDS)
                    .build();

            Request httpReq = new Request.Builder()
                    .url(baseUrl + "/embeddings")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(httpReq).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    throw new IOException("Embedding API返回错误(HTTP " + response.code() + "): " + respBody);
                }

                EmbeddingResponse embResp = objectMapper.readValue(respBody, EmbeddingResponse.class);

                // 按 index 排序结果
                List<List<Float>> vectors = new ArrayList<>(texts.size());
                for (int i = 0; i < texts.size(); i++) {
                    vectors.add(null);
                }
                for (EmbeddingData data : embResp.getData()) {
                    if (data.getIndex() < vectors.size()) {
                        vectors.set(data.getIndex(), data.getEmbedding());
                    }
                }

                return vectors;
            }
        } catch (IOException e) {
            throw new RuntimeException("Embedding HTTP请求失败: " + e.getMessage(), e);
        }
    }
}
