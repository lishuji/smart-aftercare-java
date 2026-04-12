package com.smartaftercare.service;

import com.smartaftercare.config.AppProperties;
import com.smartaftercare.model.Document;
import com.smartaftercare.model.VectorSlice;
import com.smartaftercare.repository.DocumentRepository;
import com.smartaftercare.repository.MilvusRepository;
import com.smartaftercare.repository.MinioRepository;
import com.smartaftercare.util.TextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档处理服务
 * <p>
 * 替代 Go 的 internal/service/document_service.go
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final MilvusRepository milvusRepository;
    private final MinioRepository minioRepository;
    private final AppProperties appProperties;

    /**
     * 上传并处理家电说明书
     */
    @Transactional
    public Document uploadAndProcess(String filePath, String brand, String modelName, String uploader) throws IOException {
        Path path = Path.of(filePath);
        long fileSize = Files.size(path);
        String filename = path.getFileName().toString();
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "pdf";

        // 创建文档记录
        Document doc = new Document();
        doc.setFilename(filename);
        doc.setFileType(ext);
        doc.setBrand(brand);
        doc.setModel(modelName);
        doc.setUploader(uploader);
        doc.setUploadTime(LocalDateTime.now());
        doc.setStatus("processing");
        doc.setFileSize(fileSize);
        doc.setFilePath(filePath);

        documentRepository.save(doc);

        // 异步处理文档
        processDocumentAsync(doc.getId(), filePath, brand, modelName);

        return doc;
    }

    /**
     * 异步处理文档核心流程：解析 → 切片 → 向量化 → 入库
     */
    @Async("documentProcessExecutor")
    public void processDocumentAsync(Long docId, String filePath, String brand, String modelName) {
        try {
            Document doc = documentRepository.findById(docId).orElseThrow();
            log.info("开始处理文档: {} (ID: {})", doc.getFilename(), docId);

            // 1. 解析文件（简化实现：读取文本内容）
            String content = Files.readString(Path.of(filePath));

            doc.setPageCount(1); // 简化处理

            // 2. 提取章节结构
            List<TextUtil.Chapter> chapters = TextUtil.parseChapters(content);
            log.info("提取到 {} 个章节", chapters.size());

            // 3. 文本切片
            List<String> textSlices = TextUtil.splitText(content, 300, 30);
            List<VectorSlice> slices = new ArrayList<>();

            TextUtil.Chapter currentChapter = chapters.isEmpty() ?
                    new TextUtil.Chapter("前言", 1, 1, 1) : chapters.get(0);

            for (String sliceText : textSlices) {
                if (sliceText.isBlank()) continue;

                Map<String, String> metadata = new HashMap<>();
                metadata.put("brand", brand);
                metadata.put("model", modelName);
                metadata.put("chapter", currentChapter.title());
                metadata.put("page", "1");
                metadata.put("source", doc.getFilename());

                slices.add(new VectorSlice(sliceText, metadata));
            }

            doc.setSliceCount(slices.size());
            log.info("文本切片完成，共 {} 个切片", slices.size());

            // 4. 向量化并写入 Milvus
            if (!slices.isEmpty()) {
                AppProperties.DoubaoProperties doubaoCfg = appProperties.getDoubao();
                milvusRepository.insertSlices(slices, doubaoCfg.getApiKey(), doubaoCfg.getEmbeddingModel());
                log.info("向量化入库完成");
            }

            // 5. 上传原始文件到 MinIO
            String objectKey = String.format("documents/%s/%s/%s", brand, modelName, doc.getFilename());
            try {
                minioRepository.uploadFile(filePath, objectKey);
            } catch (Exception e) {
                log.warn("原始文件上传MinIO失败: {}", e.getMessage());
            }

            // 6. 更新文档状态
            doc.setStatus("processed");
            documentRepository.save(doc);
            log.info("文档处理完成: {} (ID: {}, 切片: {})", doc.getFilename(), docId, slices.size());

        } catch (Exception e) {
            log.error("文档处理失败[{}]: {}", docId, e.getMessage(), e);
            documentRepository.findById(docId).ifPresent(doc -> {
                doc.setStatus("failed");
                doc.setRemark(e.getMessage());
                documentRepository.save(doc);
            });
        }
    }

    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Page<Document> listDocuments(String brand, String model, int page, int pageSize) {
        return documentRepository.findByBrandAndModel(brand, model, PageRequest.of(page - 1, pageSize));
    }

    @Transactional
    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
    }
}
