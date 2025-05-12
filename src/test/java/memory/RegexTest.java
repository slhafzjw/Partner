package memory;

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
}
