package com.smartaftercare.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartaftercare.config.AppProperties;
import com.smartaftercare.model.Document;
import com.smartaftercare.model.VectorSlice;
import com.smartaftercare.repository.DocumentRepository;
import com.smartaftercare.repository.MilvusRepository;
import com.smartaftercare.repository.MinioRepository;
import com.smartaftercare.util.TextUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** AI 识别品牌/型号时截取的最大文本长度（取文档开头部分） */
    private static final int AUTO_DETECT_TEXT_LIMIT = 2000;

    /**
     * AI 识别结果 DTO
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrandModelDetection {
        private String brand;
        private String model;
    }

    /**
     * 上传并处理家电说明书
     * <p>
     * brand 和 modelName 可以为 null 或空字符串，此时在异步处理阶段通过 AI 自动识别。
     */
    @Transactional
    public Document uploadAndProcess(String filePath, String brand, String modelName, String uploader) throws IOException {
        Path path = Path.of(filePath);
        long fileSize = Files.size(path);
        String filename = path.getFileName().toString();
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "pdf";

        // 创建文档记录（brand/model 可能为空，后续 AI 补充）
        Document doc = new Document();
        doc.setFilename(filename);
        doc.setFileType(ext);
        doc.setBrand(brand != null && !brand.isBlank() ? brand : null);
        doc.setModel(modelName != null && !modelName.isBlank() ? modelName : null);
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
     * 异步处理文档核心流程：AI识别品牌型号 → 解析 → 切片 → 向量化 → 入库
     */
    @Async("documentProcessExecutor")
    public void processDocumentAsync(Long docId, String filePath, String brand, String modelName) {
        try {
            Document doc = documentRepository.findById(docId).orElseThrow();
            log.info("开始处理文档: {} (ID: {})", doc.getFilename(), docId);

            // 1. 解析文件内容（支持 PDF/DOC/DOCX/TXT）
            String content = extractText(filePath);
            if (content == null || content.isBlank()) {
                throw new IOException("文档内容为空或无法解析，请确认文件格式正确");
            }
            log.info("文档文本提取完成，共 {} 字符", content.length());

            doc.setPageCount(countPages(filePath));

            // ★ AI 自动识别品牌和型号（当用户未提供时）
            boolean needDetectBrand = (brand == null || brand.isBlank());
            boolean needDetectModel = (modelName == null || modelName.isBlank());

            if (needDetectBrand || needDetectModel) {
                log.info("品牌/型号未完整提供，启动 AI 自动识别 (brand={}, model={})", brand, modelName);
                try {
                    BrandModelDetection detected = detectBrandAndModel(content, doc.getFilename());
                    if (detected != null) {
                        if (needDetectBrand && detected.getBrand() != null && !detected.getBrand().isBlank()) {
                            brand = detected.getBrand().trim();
                            log.info("AI 识别品牌: {}", brand);
                        }
                        if (needDetectModel && detected.getModel() != null && !detected.getModel().isBlank()) {
                            modelName = detected.getModel().trim();
                            log.info("AI 识别型号: {}", modelName);
                        }
                    }
                } catch (Exception e) {
                    log.warn("AI 自动识别品牌/型号失败，将使用默认值: {}", e.getMessage());
                }

                // 兜底：如果 AI 也未能识别，使用默认值
                if (brand == null || brand.isBlank()) {
                    brand = "未知品牌";
                }
                if (modelName == null || modelName.isBlank()) {
                    modelName = "未知型号";
                }

                // 更新文档记录中的品牌和型号
                doc.setBrand(brand);
                doc.setModel(modelName);
                documentRepository.save(doc);
                log.info("文档品牌/型号已更新: brand={}, model={}", brand, modelName);
            }

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
            log.info("文档处理完成: {} (ID: {}, 品牌: {}, 型号: {}, 切片: {})",
                    doc.getFilename(), docId, brand, modelName, slices.size());

        } catch (Exception e) {
            log.error("文档处理失败[{}]: {}", docId, e.getMessage(), e);
            documentRepository.findById(docId).ifPresent(doc -> {
                doc.setStatus("failed");
                doc.setRemark(e.getMessage());
                documentRepository.save(doc);
            });
        }
    }

    /**
     * 通过大模型 AI 从文档内容中智能识别品牌和型号
     *
     * @param content  文档全文内容
     * @param filename 文件名（也可能包含品牌/型号线索）
     * @return 识别结果，包含 brand 和 model 字段
     */
    private BrandModelDetection detectBrandAndModel(String content, String filename) throws IOException {
        AppProperties.DoubaoProperties doubaoCfg = appProperties.getDoubao();
        if (doubaoCfg.getApiKey() == null || doubaoCfg.getApiKey().isBlank()) {
            log.warn("豆包 API Key 未配置，无法进行 AI 品牌/型号识别");
            return null;
        }
        if (doubaoCfg.getChatModel() == null || doubaoCfg.getChatModel().isBlank()) {
            log.warn("豆包 Chat 模型未配置，无法进行 AI 品牌/型号识别");
            return null;
        }

        // 截取文档开头部分文本（品牌型号通常出现在封面/前几页）
        String textSnippet = content.length() > AUTO_DETECT_TEXT_LIMIT
                ? content.substring(0, AUTO_DETECT_TEXT_LIMIT)
                : content;

        String prompt = String.format("""
                请从以下家电说明书的文本内容和文件名中，识别出该家电产品的品牌（brand）和型号（model）。

                ## 要求
                1. 品牌请给出中文名称（如有英文品牌名也可以，优先中文）
                2. 型号请给出完整的型号编号（如 KFR-35GW/WDAA3、XQB90-36SP 等）
                3. 如果文档中有多个型号，请返回最主要的那个（通常是封面/标题页的型号）
                4. 如果无法确定，对应字段返回空字符串 ""
                5. **只返回 JSON，不要返回其他任何内容**

                ## 文件名
                %s

                ## 文档开头内容
                %s

                ## 返回格式（严格 JSON）
                {"brand": "品牌名", "model": "型号"}
                """, filename, textSnippet);

        DoubaoClient client = new DoubaoClient(doubaoCfg.getApiKey(), doubaoCfg.getChatModel(), doubaoCfg.getBaseUrl());
        String response = client.generate(prompt, 0.1, 256);

        log.debug("AI 品牌/型号识别原始返回: {}", response);

        // 提取 JSON 部分（大模型可能返回包含 markdown 代码块的内容）
        String json = extractJson(response);
        if (json == null) {
            log.warn("AI 返回内容中未找到有效 JSON: {}", response);
            return null;
        }

        return objectMapper.readValue(json, BrandModelDetection.class);
    }

    /**
     * 从大模型返回内容中提取 JSON 字符串
     * 支持直接返回 JSON 或包裹在 ```json ... ``` 代码块中的情况
     */
    private static String extractJson(String text) {
        if (text == null || text.isBlank()) return null;

        text = text.strip();

        // 尝试提取 ```json ... ``` 代码块
        int jsonBlockStart = text.indexOf("```json");
        if (jsonBlockStart >= 0) {
            int contentStart = text.indexOf('\n', jsonBlockStart);
            if (contentStart >= 0) {
                int contentEnd = text.indexOf("```", contentStart);
                if (contentEnd > contentStart) {
                    return text.substring(contentStart, contentEnd).strip();
                }
            }
        }

        // 尝试提取 ``` ... ``` 代码块
        int blockStart = text.indexOf("```");
        if (blockStart >= 0) {
            int contentStart = text.indexOf('\n', blockStart);
            if (contentStart >= 0) {
                int contentEnd = text.indexOf("```", contentStart);
                if (contentEnd > contentStart) {
                    return text.substring(contentStart, contentEnd).strip();
                }
            }
        }

        // 尝试直接找 { ... }
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Page<Document> listDocuments(String brand, String model, int page, int pageSize) {
        return documentRepository.findByBrandAndModel(brand, model, PageRequest.of(page - 1, pageSize));
    }

    /**
     * 重试处理失败的文档
     * <p>
     * 重置文档状态为 processing，清除错误信息，重新触发异步处理流程。
     */
    @Transactional
    public Document retryDocument(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("文档不存在: " + id));

        if (!"failed".equals(doc.getStatus())) {
            throw new IllegalStateException("只有处理失败的文档才能重试，当前状态: " + doc.getStatus());
        }

        log.info("重试处理文档: {} (ID: {})", doc.getFilename(), id);

        // 重置状态
        doc.setStatus("processing");
        doc.setRemark(null);
        documentRepository.save(doc);

        // 重新触发异步处理（brand/model 传入当前值，如果之前已经 AI 识别过则保留）
        processDocumentAsync(doc.getId(), doc.getFilePath(), doc.getBrand(), doc.getModel());

        return doc;
    }

    @Transactional
    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
    }

    // ==================== 文件解析方法 ====================

    /**
     * 根据文件扩展名提取文本内容（支持 PDF、DOC、DOCX、TXT）
     */
    private String extractText(String filePath) throws IOException {
        String ext = getFileExtension(filePath);

        return switch (ext) {
            case "pdf" -> extractTextFromPdf(filePath);
            case "doc" -> extractTextFromDoc(filePath);
            case "docx" -> extractTextFromDocx(filePath);
            case "txt" -> Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            default -> throw new IOException("不支持的文件格式: " + ext);
        };
    }

    /**
     * 从 PDF 文件提取文本
     */
    private String extractTextFromPdf(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new java.io.File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 从 DOC 文件提取文本（旧版 Word 格式）
     */
    private String extractTextFromDoc(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * 从 DOCX 文件提取文本（新版 Word 格式）
     */
    private String extractTextFromDocx(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * 获取文件页数（仅 PDF 有效，其他格式返回 1）
     */
    private int countPages(String filePath) {
        String ext = getFileExtension(filePath);
        if ("pdf".equals(ext)) {
            try (PDDocument document = Loader.loadPDF(new java.io.File(filePath))) {
                return document.getNumberOfPages();
            } catch (IOException e) {
                log.warn("获取 PDF 页数失败: {}", e.getMessage());
            }
        }
        return 1;
    }

    /**
     * 获取文件扩展名（小写）
     */
    private static String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot >= 0) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
}
