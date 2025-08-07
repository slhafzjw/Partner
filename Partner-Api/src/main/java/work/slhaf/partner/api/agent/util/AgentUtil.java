package work.slhaf.partner.api.agent.util;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class AgentUtil {

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

    public static Set<Class<?>> collectExtendedClasses(Class<?> clazz, Class<?> targetClass) {
        Set<Class<?>> classes = new HashSet<>();
        collectExtendedClasses(classes, clazz, targetClass);
        return classes;
    }

    private static void collectExtendedClasses(Set<Class<?>> classes, Class<?> clazz, Class<?> target) {
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null || superclass == target) {
            return;
        }
        collectExtendedClasses(classes, superclass, target);
        classes.add(superclass);
        collectInterfaces(clazz, classes);
    }

    private static void collectInterfaces(Class<?> clazz, Set<Class<?>> classes) {
        for (Class<?> type : clazz.getInterfaces()) {
            if (classes.add(type)) {
                collectInterfaces(type, classes);
            }
        }
    }}
