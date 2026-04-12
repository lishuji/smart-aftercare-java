package com.smartaftercare.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本处理工具类
 * <p>
 * 替代 Go 的 internal/util/prompt.go (关键词提取) 和 internal/util/slice.go (文本切片/章节)
 */
public final class TextUtil {

    private TextUtil() {}

    // 常见家电操作关键词
    private static final List<String> OPERATION_KEYWORDS = List.of(
            "开机", "关机", "启动", "停止", "暂停",
            "制冷", "制热", "除湿", "送风", "自动",
            "定时", "预约", "睡眠", "节能", "静音",
            "温度", "风速", "风向", "摆风",
            "清洗", "保养", "维护", "清洁", "消毒",
            "安装", "拆卸", "移机", "加氟",
            "遥控器", "面板", "显示屏", "指示灯",
            "滤网", "蒸发器", "冷凝器", "压缩机",
            "排水", "进水", "出水", "漏水",
            "噪音", "异响", "震动", "振动",
            "不制冷", "不制热", "不出风", "不启动",
            "漏电", "跳闸", "短路",
            "wifi", "智能", "APP", "联网"
    );

    // 故障代码前缀
    private static final List<String> ERROR_CODE_PREFIXES = List.of("E", "F", "P", "H", "L", "U", "C");

    /**
     * 从查询中提取家电相关关键词
     */
    public static List<String> extractApplianceKeywords(String query) {
        Set<String> keywords = new LinkedHashSet<>();

        String queryLower = query.toLowerCase();
        for (String kw : OPERATION_KEYWORDS) {
            if (query.contains(kw) || queryLower.contains(kw.toLowerCase())) {
                keywords.add(kw);
            }
        }

        // 提取故障代码（如 E1, F2, P3, H6 等）
        String queryUpper = query.toUpperCase();
        for (String prefix : ERROR_CODE_PREFIXES) {
            for (int i = 0; i <= 99; i++) {
                String code = prefix + i;
                if (queryUpper.contains(code)) {
                    keywords.add(code);
                }
            }
        }

        // 如果没有提取到关键词，按空格和标点分割
        if (keywords.isEmpty()) {
            String cleaned = query.replaceAll("[，。？！、；：,\\.?!(（)）]", " ");
            String[] words = cleaned.split("\\s+");
            for (String w : words) {
                w = w.trim();
                if (w.length() >= 2) {
                    keywords.add(w);
                }
            }
        }

        return new ArrayList<>(keywords);
    }

    /**
     * 按固定长度切片文本（支持重叠）
     */
    public static List<String> splitText(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        text = text.strip();
        int textLen = text.codePointCount(0, text.length());

        if (textLen <= chunkSize) {
            return List.of(text);
        }

        int[] codePoints = text.codePoints().toArray();
        List<String> slices = new ArrayList<>();
        int start = 0;

        while (start < textLen) {
            int end = Math.min(start + chunkSize, textLen);
            String chunk = new String(codePoints, start, end - start).strip();
            if (!chunk.isEmpty()) {
                slices.add(chunk);
            }
            if (end >= textLen) break;
            start = end - overlap;
            if (start <= 0) break;
        }

        return slices;
    }

    /**
     * 章节信息
     */
    public record Chapter(String title, int startPage, int endPage, int level) {
        public Chapter(String title, int startPage, int level) {
            this(title, startPage, 9999, level);
        }
    }

    // 章节匹配正则
    private static final Pattern CHAPTER_PATTERN_1 = Pattern.compile("第[一二三四五六七八九十\\d]+[章节][\\s:：]*(.+)");
    private static final Pattern CHAPTER_PATTERN_2 = Pattern.compile("^(\\d+(?:\\.\\d+)*)[.、\\s]+(.+)");
    private static final Pattern CHAPTER_PATTERN_3 = Pattern.compile("^[（(]?[一二三四五六七八九十]+[）)、][.、\\s]*(.+)");

    /**
     * 从文本中提取章节结构
     */
    public static List<Chapter> parseChapters(String text) {
        List<Chapter> chapters = new ArrayList<>();

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.strip();
            if (line.isEmpty() || line.codePointCount(0, line.length()) > 50) continue;

            Matcher m1 = CHAPTER_PATTERN_1.matcher(line);
            Matcher m2 = CHAPTER_PATTERN_2.matcher(line);
            Matcher m3 = CHAPTER_PATTERN_3.matcher(line);

            if (m1.find() || m2.find() || m3.find()) {
                int level = 1;
                if (line.contains(".")) {
                    level = (int) line.chars().filter(c -> c == '.').count() + 1;
                }
                chapters.add(new Chapter(line, 1, level));
            }
        }

        // 设置结束页
        List<Chapter> result = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            int endPage = (i < chapters.size() - 1) ? chapters.get(i + 1).startPage() : 9999;
            result.add(new Chapter(ch.title(), ch.startPage(), endPage, ch.level()));
        }

        return result;
    }
}
