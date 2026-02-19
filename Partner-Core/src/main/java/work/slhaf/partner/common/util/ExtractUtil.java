package work.slhaf.partner.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractUtil {
    public static String extractJson(String jsonStr) {
        jsonStr = jsonStr.replace("“", "\"").replace("”", "\"");
        int start = jsonStr.indexOf("{");
        int end = jsonStr.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return jsonStr.substring(start, end + 1);
        }
        return jsonStr;
    }

    public static String extractUserId(String messageContent) {
        Pattern pattern = Pattern.compile("\\[.*\\(([^)]+)\\)\\]");
        Matcher matcher = pattern.matcher(messageContent);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    public static String fixTopicPath(String topicPath) {
        String[] parts = topicPath.split("->");
        List<String> cleanedParts = new ArrayList<>();

        for (String part : parts) {
            // 修正正则表达式，正确移除 [xxx] 部分
            String cleaned = part.replaceAll("\\[[^\\]]*\\]", "").trim();
            if (!cleaned.isEmpty()) { // 忽略空字符串
                cleanedParts.add(cleaned);
            }
        }

        return String.join("->", cleanedParts);
    }
}
