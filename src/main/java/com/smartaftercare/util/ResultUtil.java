package com.smartaftercare.util;

import com.smartaftercare.model.VectorSlice;

import java.util.*;

/**
 * 检索结果处理工具类
 * <p>
 * 替代 Go 的 internal/util/result.go
 */
public final class ResultUtil {

    private ResultUtil() {}

    // 章节优先级（数字越小优先级越高）
    private static final Map<String, Integer> CHAPTER_PRIORITY = Map.ofEntries(
            Map.entry("故障", 1), Map.entry("排查", 1), Map.entry("错误", 1),
            Map.entry("维修", 1), Map.entry("报警", 1), Map.entry("代码", 1),
            Map.entry("操作", 2), Map.entry("使用", 2), Map.entry("功能", 2),
            Map.entry("指南", 2), Map.entry("说明", 2),
            Map.entry("保养", 3), Map.entry("维护", 3), Map.entry("清洁", 3),
            Map.entry("安装", 4),
            Map.entry("规格", 5), Map.entry("参数", 5)
    );

    /**
     * 合并关键词检索和向量检索结果，并按章节优先级排序
     */
    public static List<VectorSlice> mergeAndRankResults(List<VectorSlice> keywordResults, List<VectorSlice> vectorResults) {
        Set<String> seen = new HashSet<>();
        List<VectorSlice> merged = new ArrayList<>();

        // 关键词结果优先
        if (keywordResults != null) {
            for (VectorSlice r : keywordResults) {
                String key = truncateContent(r.getContent(), 100);
                if (seen.add(key)) {
                    merged.add(r);
                }
            }
        }

        // 然后添加向量结果
        if (vectorResults != null) {
            for (VectorSlice r : vectorResults) {
                String key = truncateContent(r.getContent(), 100);
                if (seen.add(key)) {
                    merged.add(r);
                }
            }
        }

        // 按章节优先级排序
        merged.sort(Comparator.comparingInt(s ->
                getChapterPriority(s.getMetadata() != null ? s.getMetadata().getOrDefault("chapter", "") : "")));

        return merged;
    }

    private static int getChapterPriority(String chapterTitle) {
        int minPriority = 99;
        for (Map.Entry<String, Integer> entry : CHAPTER_PRIORITY.entrySet()) {
            if (chapterTitle.contains(entry.getKey()) && entry.getValue() < minPriority) {
                minPriority = entry.getValue();
            }
        }
        return minPriority;
    }

    private static String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        int cpCount = content.codePointCount(0, content.length());
        if (cpCount > maxLen) {
            return content.substring(0, content.offsetByCodePoints(0, maxLen));
        }
        return content;
    }

    /**
     * 格式化来源信息
     */
    public static List<String> formatSources(List<VectorSlice> results) {
        Set<String> seen = new LinkedHashSet<>();
        for (VectorSlice r : results) {
            Map<String, String> meta = r.getMetadata();
            if (meta == null) continue;
            String source = meta.getOrDefault("brand", "") + " " +
                    meta.getOrDefault("model", "") +
                    "（第" + meta.getOrDefault("page", "") + "页，" +
                    meta.getOrDefault("chapter", "") + "）";
            seen.add(source);
        }
        return new ArrayList<>(seen);
    }

    /**
     * 收集结果中的图片 URL
     */
    public static List<String> collectImageURLs(List<VectorSlice> results) {
        Set<String> seen = new LinkedHashSet<>();
        for (VectorSlice r : results) {
            if (r.getMetadata() != null) {
                String imgUrl = r.getMetadata().get("image_url");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    seen.add(imgUrl);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * 从检索结果构建上下文文本
     */
    public static String buildContextText(List<VectorSlice> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append("\n---\n");
            VectorSlice r = results.get(i);
            if (r.getMetadata() != null) {
                String chapter = r.getMetadata().get("chapter");
                if (chapter != null && !chapter.isEmpty()) {
                    sb.append("[").append(chapter).append("] ");
                }
            }
            sb.append(r.getContent());
        }
        return sb.toString();
    }
}
