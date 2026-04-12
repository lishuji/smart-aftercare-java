package com.smartaftercare.repository;

import com.smartaftercare.config.AppProperties;
import com.smartaftercare.model.VectorSlice;
import com.smartaftercare.service.DoubaoClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Milvus 向量库数据访问层
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MilvusRepository {

    private static final int VECTOR_DIM = 768;

    private final MilvusServiceClient milvusClient;
    private final AppProperties appProperties;

    @PostConstruct
    public void init() {
        String collectionName = appProperties.getMilvus().getCollectionName();

        // 检查集合是否存在
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());

        if (hasCollection.getData() == null || !hasCollection.getData()) {
            createCollection(collectionName);
        }

        // 加载集合到内存
        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());

        log.info("Milvus 集合 '{}' 已加载", collectionName);
    }

    private void createCollection(String collectionName) {
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(4000)
                .build();

        FieldType brandField = FieldType.newBuilder()
                .withName("brand")
                .withDataType(DataType.VarChar)
                .withMaxLength(100)
                .build();

        FieldType modelField = FieldType.newBuilder()
                .withName("model")
                .withDataType(DataType.VarChar)
                .withMaxLength(100)
                .build();

        FieldType chapterField = FieldType.newBuilder()
                .withName("chapter")
                .withDataType(DataType.VarChar)
                .withMaxLength(200)
                .build();

        FieldType pageField = FieldType.newBuilder()
                .withName("page")
                .withDataType(DataType.VarChar)
                .withMaxLength(20)
                .build();

        FieldType sourceField = FieldType.newBuilder()
                .withName("source")
                .withDataType(DataType.VarChar)
                .withMaxLength(500)
                .build();

        FieldType imageUrlField = FieldType.newBuilder()
                .withName("image_url")
                .withDataType(DataType.VarChar)
                .withMaxLength(1000)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withShardsNum(2)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(brandField)
                .addFieldType(modelField)
                .addFieldType(chapterField)
                .addFieldType(pageField)
                .addFieldType(sourceField)
                .addFieldType(imageUrlField)
                .build();

        milvusClient.createCollection(createParam);

        // 创建向量索引（IVF_FLAT）
        milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName("vector")
                        .withIndexType(IndexType.IVF_FLAT)
                        .withMetricType(MetricType.L2)
                        .withExtraParam("{\"nlist\":128}")
                        .build());

        log.info("Milvus 集合 '{}' 创建完成", collectionName);
    }

    /**
     * 插入文本切片（向量化 + 写入 Milvus）
     */
    public void insertSlices(List<VectorSlice> slices, String apiKey, String embeddingModel) {
        if (slices == null || slices.isEmpty()) return;

        String collectionName = appProperties.getMilvus().getCollectionName();

        // 1. 批量向量化
        List<String> contents = slices.stream().map(VectorSlice::getContent).toList();
        int batchSize = 16;
        List<List<Float>> allVectors = new ArrayList<>();

        for (int i = 0; i < contents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, contents.size());
            List<String> batch = contents.subList(i, end);
            List<List<Float>> vectors = DoubaoClient.generateEmbeddings(apiKey, embeddingModel, batch);
            allVectors.addAll(vectors);
        }

        // 2. 构造插入数据
        List<List<Float>> vectorData = allVectors;
        List<String> contentList = new ArrayList<>();
        List<String> brandList = new ArrayList<>();
        List<String> modelList = new ArrayList<>();
        List<String> chapterList = new ArrayList<>();
        List<String> pageList = new ArrayList<>();
        List<String> sourceList = new ArrayList<>();
        List<String> imageUrlList = new ArrayList<>();

        for (VectorSlice slice : slices) {
            contentList.add(slice.getContent());
            brandList.add(getMetadata(slice, "brand"));
            modelList.add(getMetadata(slice, "model"));
            chapterList.add(getMetadata(slice, "chapter"));
            pageList.add(getMetadata(slice, "page"));
            sourceList.add(getMetadata(slice, "source"));
            imageUrlList.add(getMetadata(slice, "image_url"));
        }

        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field("vector", vectorData),
                new InsertParam.Field("content", contentList),
                new InsertParam.Field("brand", brandList),
                new InsertParam.Field("model", modelList),
                new InsertParam.Field("chapter", chapterList),
                new InsertParam.Field("page", pageList),
                new InsertParam.Field("source", sourceList),
                new InsertParam.Field("image_url", imageUrlList)
        );

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);
        milvusClient.flush(FlushParam.newBuilder()
                .addCollectionName(collectionName)
                .build());

        log.info("向量数据插入完成，共 {} 条", slices.size());
    }

    /**
     * 向量检索
     */
    public List<VectorSlice> searchByVector(String query, String apiKey, String embeddingModel, String filter, int topK) {
        String collectionName = appProperties.getMilvus().getCollectionName();

        // 1. 查询向量化
        List<Float> vector = DoubaoClient.generateEmbedding(apiKey, embeddingModel, query);

        // 2. 执行检索
        List<String> outputFields = List.of("content", "brand", "model", "chapter", "page", "source", "image_url");

        SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.L2)
                .withOutFields(outputFields)
                .withTopK(topK)
                .withVectors(List.of(vector))
                .withVectorFieldName("vector")
                .withParams("{\"nprobe\":10}");

        if (filter != null && !filter.isEmpty()) {
            searchBuilder.withExpr(filter);
        }

        R<SearchResults> searchResults = milvusClient.search(searchBuilder.build());
        if (searchResults.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("向量检索失败: " + searchResults.getMessage());
        }

        // 3. 解析结果
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getData().getResults());
        return parseSearchResults(wrapper, topK);
    }

    /**
     * 关键词检索
     */
    public List<VectorSlice> searchByKeywords(List<String> keywords, String filter, int topK) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptyList();

        String collectionName = appProperties.getMilvus().getCollectionName();

        // 构造关键词查询表达式（前缀匹配）
        StringBuilder keywordExpr = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) keywordExpr.append(" or ");
            keywordExpr.append(String.format("content like \"%s%%\"", keywords.get(i)));
        }

        String finalExpr = keywordExpr.toString();
        if (filter != null && !filter.isEmpty()) {
            finalExpr = filter + " and (" + keywordExpr + ")";
        }

        List<String> outputFields = List.of("content", "brand", "model", "chapter", "page", "source", "image_url");

        QueryParam queryParam = QueryParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(finalExpr)
                .withOutFields(outputFields)
                .withLimit((long) topK)
                .build();

        R<QueryResults> queryResults = milvusClient.query(queryParam);
        if (queryResults.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("关键词检索失败: " + queryResults.getMessage());
        }

        QueryResultsWrapper wrapper = new QueryResultsWrapper(queryResults.getData());
        return parseQueryResults(wrapper, topK);
    }

    /**
     * 获取集合统计信息
     */
    public Map<String, String> getCollectionStats() {
        String collectionName = appProperties.getMilvus().getCollectionName();
        R<GetCollectionStatisticsResponse> response = milvusClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());

        Map<String, String> stats = new HashMap<>();
        if (response.getData() != null) {
            response.getData().getStatsList().forEach(kv ->
                    stats.put(kv.getKey(), kv.getValue()));
        }
        return stats;
    }

    // ==================== 辅助方法 ====================

    private List<VectorSlice> parseSearchResults(SearchResultsWrapper wrapper, int topK) {
        List<VectorSlice> slices = new ArrayList<>();
        try {
            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
            for (int i = 0; i < Math.min(idScores.size(), topK); i++) {
                Map<String, String> metadata = new HashMap<>();
                VectorSlice slice = new VectorSlice();

                SearchResultsWrapper.IDScore idScore = idScores.get(i);
                slice.setScore(idScore.getScore());

                // 从 FieldData 提取字段值
                for (String field : List.of("content", "brand", "model", "chapter", "page", "source", "image_url")) {
                    try {
                        Object val = wrapper.getFieldData(field, 0).get(i);
                        String strVal = val != null ? val.toString() : "";
                        if ("content".equals(field)) {
                            slice.setContent(strVal);
                        } else if (!strVal.isEmpty()) {
                            metadata.put(field, strVal);
                        }
                    } catch (Exception ignored) {
                    }
                }

                slice.setMetadata(metadata);
                slices.add(slice);
            }
        } catch (Exception e) {
            log.warn("解析向量检索结果异常: {}", e.getMessage());
        }
        return slices;
    }

    private List<VectorSlice> parseQueryResults(QueryResultsWrapper wrapper, int topK) {
        List<VectorSlice> slices = new ArrayList<>();
        try {
            // 获取行数
            List<?> contentCol = wrapper.getFieldWrapper("content").getFieldData();
            int rowCount = Math.min(contentCol.size(), topK);

            for (int i = 0; i < rowCount; i++) {
                Map<String, String> metadata = new HashMap<>();
                VectorSlice slice = new VectorSlice();

                for (String field : List.of("content", "brand", "model", "chapter", "page", "source", "image_url")) {
                    try {
                        Object val = wrapper.getFieldWrapper(field).getFieldData().get(i);
                        String strVal = val != null ? val.toString() : "";
                        if ("content".equals(field)) {
                            slice.setContent(strVal);
                        } else if (!strVal.isEmpty()) {
                            metadata.put(field, strVal);
                        }
                    } catch (Exception ignored) {
                    }
                }

                slice.setMetadata(metadata);
                slices.add(slice);
            }
        } catch (Exception e) {
            log.warn("解析关键词查询结果异常: {}", e.getMessage());
        }
        return slices;
    }

    private String getMetadata(VectorSlice slice, String key) {
        if (slice.getMetadata() == null) return "";
        return slice.getMetadata().getOrDefault(key, "");
    }
}
