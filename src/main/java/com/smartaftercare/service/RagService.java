package com.smartaftercare.service;

import com.smartaftercare.config.AppProperties;
import com.smartaftercare.model.VectorSlice;
import com.smartaftercare.repository.MilvusRepository;
import com.smartaftercare.repository.RedisRepository;
import com.smartaftercare.util.PromptUtil;
import com.smartaftercare.util.ResultUtil;
import com.smartaftercare.util.TextUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * RAG 检索+生成服务
 * <p>
 * 替代 Go 的 internal/service/rag_service.go
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final MilvusRepository milvusRepository;
    private final RedisRepository redisRepository;
    private final AppProperties appProperties;

    private DoubaoClient doubaoClient;

    private DoubaoClient getDoubaoClient() {
        if (doubaoClient == null) {
            AppProperties.DoubaoProperties cfg = appProperties.getDoubao();
            doubaoClient = new DoubaoClient(cfg.getApiKey(), cfg.getChatModel(), cfg.getBaseUrl());
        }
        return doubaoClient;
    }

    @Data
    public static class QAResult {
        private String answer;
        private List<String> sources;
        private List<String> images;
        private boolean cacheHit;
    }

    /**
     * 智能问答核心流程（缓存查询 → 检索 → 生成）
     */
    public QAResult qa(String query, String brand, String modelName) {
        AppProperties.DoubaoProperties doubaoCfg = appProperties.getDoubao();

        // 1. 构建缓存键
        String cacheKey = "rag:qa:" + modelName + ":" + query;

        // 2. 查询缓存
        RedisRepository.QACacheData cached = redisRepository.getQACache(cacheKey);
        if (cached != null && cached.getAnswer() != null && !cached.getAnswer().isEmpty()) {
            log.info("命中缓存: {}", cacheKey);
            QAResult result = new QAResult();
            result.setAnswer(cached.getAnswer());
            result.setSources(cached.getSources());
            result.setImages(cached.getImages());
            result.setCacheHit(true);
            return result;
        }

        // 3. 构建检索过滤条件
        String filter = buildFilter(brand, modelName);

        // 4. 提取关键词
        List<String> keywords = TextUtil.extractApplianceKeywords(query);
        log.info("提取关键词: {}", keywords);

        // 5. 双重检索：关键词 + 向量
        List<VectorSlice> keywordResults = null;
        try {
            keywordResults = milvusRepository.searchByKeywords(keywords, filter, 2);
        } catch (Exception e) {
            log.warn("关键词检索失败: {}", e.getMessage());
        }

        List<VectorSlice> vectorResults = null;
        try {
            vectorResults = milvusRepository.searchByVector(query,
                    doubaoCfg.getApiKey(), doubaoCfg.getEmbeddingModel(), filter, 3);
        } catch (Exception e) {
            log.warn("向量检索失败（降级为纯大模型回答）: {}", e.getMessage());
        }

        // 6. 合并结果
        List<VectorSlice> combinedResults = ResultUtil.mergeAndRankResults(keywordResults, vectorResults);

        String answer;
        List<String> sources;
        List<String> images;

        try {
            if (combinedResults.isEmpty()) {
                // 降级：纯大模型直接回答
                log.info("无检索结果，降级为纯大模型直接回答");
                String directPrompt = PromptUtil.generateDirectPrompt(query, modelName);
                answer = getDoubaoClient().generate(directPrompt, doubaoCfg.getTemperature(), doubaoCfg.getMaxToken());
                sources = Collections.emptyList();
                images = Collections.emptyList();
            } else {
                // 基于检索内容生成回答
                String contextText = ResultUtil.buildContextText(combinedResults);
                sources = ResultUtil.formatSources(combinedResults);
                images = ResultUtil.collectImageURLs(combinedResults);

                String prompt = PromptUtil.generateAppliancePrompt(query, contextText, modelName);
                answer = getDoubaoClient().generate(prompt, doubaoCfg.getTemperature(), doubaoCfg.getMaxToken());
            }
        } catch (Exception e) {
            throw new RuntimeException("大模型生成失败: " + e.getMessage(), e);
        }

        // 7. 缓存结果（1小时过期）
        try {
            redisRepository.setQACache(cacheKey, answer, sources, images, Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("缓存设置失败: {}", e.getMessage());
        }

        QAResult result = new QAResult();
        result.setAnswer(answer);
        result.setSources(sources);
        result.setImages(images);
        return result;
    }

    /**
     * 构建 Milvus 过滤表达式
     */
    private String buildFilter(String brand, String modelName) {
        StringBuilder sb = new StringBuilder();
        if (modelName != null && !modelName.isEmpty()) {
            sb.append("model == \"").append(modelName).append("\"");
        }
        if (brand != null && !brand.isEmpty()) {
            if (sb.length() > 0) sb.append(" and ");
            sb.append("brand == \"").append(brand).append("\"");
        }
        return sb.toString();
    }
}
