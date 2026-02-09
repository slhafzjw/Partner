package work.slhaf.partner.common.util;

public class PathUtil {
    public static String buildPathStr(String... path) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            str.append(path[i]);
            if (i < path.length - 1) {
                str.append("/");
            }
        }
        return str.toString();
    }
}