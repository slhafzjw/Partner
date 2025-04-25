package work.slhaf.agent.common.util;

public class ExtractUtil {
    public static String  extractJson(String jsonStr) {
        int start = jsonStr.indexOf("{");
        int end = jsonStr.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return jsonStr.substring(start, end + 1);
        }
        return jsonStr;
    }
}
