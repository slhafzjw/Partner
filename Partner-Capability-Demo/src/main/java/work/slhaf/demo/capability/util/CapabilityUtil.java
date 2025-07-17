package work.slhaf.demo.capability.util;

import java.lang.reflect.Method;

public final class CapabilityUtil {
    public static String methodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(method.getReturnType().getName()).append(" ");
        sb.append(method.getName()).append("(");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append(paramTypes[i].getName());
            if (i < paramTypes.length - 1) sb.append(",");
        }
        sb.append(")").append(")");
        return sb.toString();
    }
}
