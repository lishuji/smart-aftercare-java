package com.smartaftercare.service;

import com.smartaftercare.model.ErrorCode;
import com.smartaftercare.repository.ErrorCodeRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 故障代码服务
 * <p>
 * 替代 Go 的 internal/service/error_code_service.go
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorCodeService {

    private final ErrorCodeRepository errorCodeRepository;
    private final RagService ragService;

    @Data
    public static class ErrorCodeResult {
        private String code;
        private String answer;
        private List<String> sources;
        private List<String> images;
        private boolean fromDb;
        private boolean fromRag;
    }

    /**
     * 故障代码查询（本地表优先，无匹配则走 RAG）
     */
    public ErrorCodeResult queryErrorCode(String code, String modelName) {
        // 1. 本地 MySQL 查询
        Optional<ErrorCode> errorCode;
        if (modelName != null && !modelName.isEmpty()) {
            errorCode = errorCodeRepository.findFirstByCodeAndModel(code, modelName);
        } else {
            errorCode = errorCodeRepository.findFirstByCode(code);
        }

        if (errorCode.isPresent()) {
            ErrorCode ec = errorCode.get();
            String answer = "故障代码" + code + "：" + ec.getReason() + "\n解决方案：" + ec.getSolution();
            List<String> sources = List.of(ec.getBrand() + " " + ec.getModel() + " 故障代码表");

            ErrorCodeResult result = new ErrorCodeResult();
            result.setCode(code);
            result.setAnswer(answer);
            result.setSources(sources);
            result.setImages(Collections.emptyList());
            result.setFromDb(true);
            return result;
        }

        log.info("本地无匹配故障代码，走RAG检索: code={}, model={}", code, modelName);

        // 2. 降级走 RAG 检索
        String ragQuery = "家电故障代码" + code + "是什么意思？如何解决？";
        RagService.QAResult qaResult = ragService.qa(ragQuery, "", modelName);

        ErrorCodeResult result = new ErrorCodeResult();
        result.setCode(code);
        result.setAnswer(qaResult.getAnswer());
        result.setSources(qaResult.getSources());
        result.setImages(qaResult.getImages());
        result.setFromRag(true);
        return result;
    }
}
