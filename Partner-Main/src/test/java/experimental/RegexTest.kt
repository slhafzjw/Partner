package experimental

import org.junit.jupiter.api.Test
import java.lang.String.join
import java.util.regex.Pattern

class RegexTest {
    @Test
    fun regexTest() {
        val examples = arrayListOf(
            "[小明(abc)] 我在开会] (te[]st)",
            "[用户(昵)称(userId)] 你好[呀]",
            "[测试账号(userId)] 这是一个(test(123))消息"
        )

        val pattern = Pattern.compile("\\[.*?\\(([^)]+)\\)\\]")

        for (example in examples) {
            val matcher = pattern.matcher(example)
            if (matcher.find()) {
                println("在 '$example' 中找到 userId: ${matcher.group(1)}")
                println()
            } else {
                println("在 '$example' 中未找到 userId")
            }
        }
    }

    @Test
    fun topicPathFixTest() {
        var a = "xxxxx[awdohno][awdsjo]"
        a = fix(a)
        println(a)
    }

    private fun fix(topicPath: String): String {
        val parts = topicPath.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val cleanedParts: MutableList<String?> = ArrayList<String?>()

        for (part in parts) {
            // 修正正则表达式，正确移除 [xxx] 部分
            val cleaned = part.replace("\\[[^\\]]*\\]".toRegex(), "").trim { it <= ' ' }
            if (!cleaned.isEmpty()) { // 忽略空字符串
                cleanedParts.add(cleaned)
            }
        }

        return join("->", cleanedParts)
    }
}
