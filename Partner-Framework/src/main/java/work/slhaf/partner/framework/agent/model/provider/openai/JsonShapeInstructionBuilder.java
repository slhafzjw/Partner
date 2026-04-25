package work.slhaf.partner.framework.agent.model.provider.openai;

import java.lang.reflect.*;
import java.util.*;

final class JsonShapeInstructionBuilder {

    private static final int MAX_DEPTH = 4;

    private JsonShapeInstructionBuilder() {
    }

    static String build(Class<?> responseType) {
        return "Return only a valid JSON object.\n"
                + "The JSON object must directly match this exact output shape for " + responseType.getSimpleName() + ":\n"
                + buildJsonShape(responseType, 0, new HashSet<>()) + "\n\n"
                + "Rules:\n"
                + "- The top-level object must directly match the shape above.\n"
                + "- Do not wrap it in \"" + responseType.getSimpleName() + "\" or any other class name.\n"
                + "- Do not rename fields or invent alternative field names.\n"
                + "- Do not output markdown, comments, explanations, or code fences.";
    }

    private static String buildJsonShape(Type type, int depth, Set<Type> visiting) {
        if (depth > MAX_DEPTH) {
            return "{}";
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)) {
                Type[] arguments = parameterizedType.getActualTypeArguments();
                if (arguments.length == 0) {
                    return "[]";
                }
                return arrayShape(buildJsonShape(arguments[0], depth + 1, visiting), depth);
            }
            if (rawType instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)) {
                return "{}";
            }
            return buildJsonShape(rawType, depth, visiting);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return arrayShape(buildJsonShape(genericArrayType.getGenericComponentType(), depth + 1, visiting), depth);
        }
        if (!(type instanceof Class<?> clazz)) {
            return "null";
        }
        if (clazz.isArray()) {
            return arrayShape(buildJsonShape(clazz.getComponentType(), depth + 1, visiting), depth);
        }
        if (clazz == String.class || clazz == Character.class || clazz == char.class) {
            return "\"\"";
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return "false";
        }
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            return "0";
        }
        if (clazz.isEnum()) {
            Object[] constants = clazz.getEnumConstants();
            return constants == null || constants.length == 0 ? "\"\"" : "\"" + constants[0] + "\"";
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            return "[]";
        }
        if (Map.class.isAssignableFrom(clazz)) {
            return "{}";
        }
        if (clazz.getName().startsWith("java.")) {
            return "\"\"";
        }
        if (visiting.contains(clazz)) {
            return "{}";
        }

        visiting.add(clazz);
        List<Field> fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !Modifier.isTransient(field.getModifiers()))
                .toList();
        if (fields.isEmpty()) {
            visiting.remove(clazz);
            return "{}";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            builder.append(indent(depth + 1))
                    .append("\"")
                    .append(field.getName())
                    .append("\": ")
                    .append(buildJsonShape(field.getGenericType(), depth + 1, visiting));
            if (i < fields.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append(indent(depth)).append("}");
        visiting.remove(clazz);
        return builder.toString();
    }

    private static String arrayShape(String itemShape, int depth) {
        return "[\n" + indent(depth + 1) + itemShape.replace("\n", "\n" + indent(depth + 1)) + "\n" + indent(depth) + "]";
    }

    private static String indent(int depth) {
        return "  ".repeat(Math.max(0, depth));
    }
}
