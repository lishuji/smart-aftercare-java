package com.smartaftercare.controller;

import com.smartaftercare.model.Document;
import com.smartaftercare.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文档管理 Controller
 * <p>
 * 替代 Go 的 internal/handler/document.go
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档上传、查询、删除等操作")
public class DocumentController {

    private final DocumentService documentService;

    private static final Set<String> ALLOWED_EXTS = Set.of(".pdf", ".doc", ".docx", ".txt");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    @PostMapping("/document/upload")
    @Operation(summary = "上传文档", description = "上传家电说明书文档（PDF/DOC/DOCX/TXT），系统将异步解析、切片、向量化入库")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "品牌名称") @RequestParam("brand") String brand,
            @Parameter(description = "型号") @RequestParam("model") String model,
            @Parameter(description = "上传人") @RequestParam(value = "uploader", required = false) String uploader) {

        // 校验文件类型
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "文件名不能为空"));
        }

        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
        if (!ALLOWED_EXTS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "不支持的文件类型，仅支持 PDF、DOC、DOCX、TXT"));
        }

        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "文件大小超过限制（最大50MB）"));
        }

        try {
            // 保存到本地上传目录
            Path uploadDir = Path.of("./uploads");
            Files.createDirectories(uploadDir);
            String saveName = brand + "_" + model + "_" + filename;
            Path savePath = uploadDir.resolve(saveName);
            file.transferTo(savePath.toFile());

            // 调用文档处理服务
            Document doc = documentService.uploadAndProcess(savePath.toString(), brand, model, uploader);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("code", 200);
            response.put("message", "文档上传成功，正在后台处理");
            response.put("data", doc);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("文档处理启动失败: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "message", "文档处理失败: " + e.getMessage()));
        }
    }

    @GetMapping("/document/{id}")
    @Operation(summary = "获取文档详情", description = "根据文档 ID 获取文档详细信息")
    public ResponseEntity<Map<String, Object>> getDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id) {

        return documentService.getDocumentById(id)
                .map(doc -> ResponseEntity.ok(Map.of("code", (Object) 200, "data", doc)))
                .orElse(ResponseEntity.status(404).body(Map.of("code", 404, "message", "文档不存在")));
    }

    @GetMapping("/documents")
    @Operation(summary = "文档列表", description = "分页查询文档列表，支持按品牌、型号筛选")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @Parameter(description = "品牌名称") @RequestParam(required = false) String brand,
            @Parameter(description = "型号") @RequestParam(required = false) String model,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {

        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 10;

        Page<Document> docs = documentService.listDocuments(brand, model, page, pageSize);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", docs.getContent());
        data.put("total", docs.getTotalElements());
        data.put("page", page);
        data.put("page_size", pageSize);

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    @DeleteMapping("/document/{id}")
    @Operation(summary = "删除文档", description = "根据文档 ID 删除文档及其关联的向量数据")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(description = "文档 ID") @PathVariable Long id) {

        try {
            documentService.deleteDocument(id);
            return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "message", "删除失败"));
        }
    }
}
