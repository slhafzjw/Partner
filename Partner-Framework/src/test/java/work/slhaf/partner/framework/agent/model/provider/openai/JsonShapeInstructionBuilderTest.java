package work.slhaf.partner.framework.agent.model.provider.openai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonShapeInstructionBuilderTest {

    @Test
    void shouldBuildShapeInstructionForComplexPojo() {
        String instruction = JsonShapeInstructionBuilder.build(ComplexResponse.class);

        assertTrue(instruction.startsWith("Return only a valid JSON object."));
        assertTrue(instruction.contains("The JSON object must directly match this exact output shape for ComplexResponse:"));
        assertTrue(instruction.contains("Do not wrap it in \"ComplexResponse\" or any other class name."));
        assertTrue(instruction.contains("Do not rename fields or invent alternative field names."));

        assertTrue(instruction.contains("\"id\": \"\""));
        assertTrue(instruction.contains("\"success\": false"));
        assertTrue(instruction.contains("\"retryCount\": 0"));
        assertTrue(instruction.contains("\"score\": 0"));
        assertTrue(instruction.contains("\"status\": \"READY\""));
        assertTrue(instruction.contains("\"tags\": ["));
        assertTrue(instruction.contains("\"items\": ["));
        assertTrue(instruction.contains("\"metadata\": {}"));
        assertTrue(instruction.contains("\"matrix\": ["));
        assertTrue(instruction.contains("\"nested\": {"));
        assertTrue(instruction.contains("\"name\": \"\""));
        assertTrue(instruction.contains("\"enabled\": false"));
        assertTrue(instruction.contains("\"notes\": ["));

        assertFalse(instruction.contains("staticValue"));
        assertFalse(instruction.contains("transientValue"));
    }

    @Test
    void shouldPreventRecursiveExpansion() {
        String instruction = JsonShapeInstructionBuilder.build(RecursiveResponse.class);

        assertTrue(instruction.contains("\"name\": \"\""));
        assertTrue(instruction.contains("\"next\": {}"));
    }

    private enum Status {
        READY,
        DONE
    }

    private static class ComplexResponse {
        private static String staticValue;
        private transient String transientValue;

        private String id;
        private boolean success;
        private int retryCount;
        private Double score;
        private Status status;
        private List<String> tags;
        private List<NestedItem> items;
        private Map<String, String> metadata;
        private int[] matrix;
        private NestedItem nested;
    }

    private static class NestedItem {
        private String name;
        private Boolean enabled;
        private List<String> notes;
    }

    private static class RecursiveResponse {
        private String name;
        private RecursiveResponse next;
    }
}
