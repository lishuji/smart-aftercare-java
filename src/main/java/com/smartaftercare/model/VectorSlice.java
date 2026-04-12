package com.smartaftercare.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量切片数据（Milvus 存储用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSlice {

    private String content;
    private Map<String, String> metadata;
    private float score;

    public VectorSlice(String content, Map<String, String> metadata) {
        this.content = content;
        this.metadata = metadata;
    }
}
