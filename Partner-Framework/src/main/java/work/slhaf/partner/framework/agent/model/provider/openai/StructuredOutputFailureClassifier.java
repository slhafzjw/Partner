package work.slhaf.partner.framework.agent.model.provider.openai;

import java.util.*;

final class StructuredOutputFailureClassifier {

    private static final List<String> TRANSIENT_FAILURE_PATTERNS = List.of(
            "timeout",
            "error reading response",
            "streamresetexception",
            "interruptedioexception",
            "sockettimeoutexception",
            "connectexception",
            "connection reset",
            "503",
            "502",
            "504",
            "429",
            "internalserverexception",
            "provider returned error",
            "openaiioexception",
            "request failed"
    );
    private static final List<String> AUTH_OR_CONFIG_FAILURE_PATTERNS = List.of(
            "没有权限",
            "not activated",
            "permission",
            "unauthorized",
            "forbidden",
            "invalid api key",
            "sslexception",
            "unsupported or unrecognized ssl message"
    );
    private static final List<String> STRUCTURED_COMPATIBILITY_FAILURE_PATTERNS = List.of(
            "response_format type is unavailable",
            "messages must contain the word 'json'",
            "messages must contain the word json",
            "structured chat completion returned empty content",
            "error parsing json:",
            "unrecognizedpropertyexception",
            "mismatchedinputexception",
            "invalidformatexception"
    );

    private StructuredOutputFailureClassifier() {
    }

    static boolean shouldDowngradeToPromptOnlyJson(Throwable failure) {
        FailureSnapshot snapshot = FailureSnapshot.from(failure);
        if (snapshot.containsAny(TRANSIENT_FAILURE_PATTERNS)) {
            return false;
        }
        if (snapshot.containsAny(AUTH_OR_CONFIG_FAILURE_PATTERNS)) {
            return false;
        }
        return snapshot.containsAny(STRUCTURED_COMPATIBILITY_FAILURE_PATTERNS);
    }

    private record FailureSnapshot(String text) {

        static FailureSnapshot from(Throwable failure) {
            Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            StringBuilder builder = new StringBuilder();
            collect(failure, builder, visited);
            return new FailureSnapshot(builder.toString().toLowerCase(Locale.ROOT));
        }

        private static void collect(Throwable throwable, StringBuilder builder, Set<Throwable> visited) {
            if (throwable == null || visited.contains(throwable)) {
                return;
            }
            visited.add(throwable);
            builder.append(throwable.getClass().getName()).append('\n');
            if (throwable.getMessage() != null) {
                builder.append(throwable.getMessage()).append('\n');
            }
            collect(throwable.getCause(), builder, visited);
            for (Throwable suppressed : throwable.getSuppressed()) {
                collect(suppressed, builder, visited);
            }
        }

        boolean containsAny(List<String> patterns) {
            return patterns.stream().anyMatch(text::contains);
        }
    }
}
