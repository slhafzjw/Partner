import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest {

//    @Test
    public void regexTest(){
        String[] examples = {
                "[小明(abc)] 我在开会] (te[]st)",
                "[用户(昵)称(userId)] 你好[呀]",
                "[测试账号(userId)] 这是一个(test(123))消息"
        };

        Pattern pattern = Pattern.compile("\\[.*?\\(([^)]+)\\)\\]");

        for (String example : examples) {
            Matcher matcher = pattern.matcher(example);
            if (matcher.find()) {
                System.out.println("在 '" + example + "' 中找到 userId: " + matcher.group(1));
                System.out.println();
            } else {
                System.out.println("在 '" + example + "' 中未找到 userId");
            }
        }

    }

//    @Test
    public void topicPathFixTest(){
        String a = "xxxxx[awdohno][awdsjo]";
        a = fix(a);
        System.out.println(a);
    }

    private String fix(String topicPath) {
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
