package work.slhaf.partner.module.action.planner.evaluator.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorResultTest {

    @Test
    void shouldReturnEmptyMapWhenPrimaryActionChainIsNull() {
        EvaluatorResult result = new EvaluatorResult();
        Map<Integer, List<String>> chain = result.getPrimaryActionChainAsMap();
        assertNotNull(chain);
        assertTrue(chain.isEmpty());
    }

    @Test
    void shouldNormalizeNullActionKeysToEmptyList() {
        EvaluatorResult result = new EvaluatorResult();
        EvaluatorResult.ChainElement element = new EvaluatorResult.ChainElement();
        element.setOrder(1);
        element.setActionKeys(null);
        result.setPrimaryActionChain(List.of(element));

        Map<Integer, List<String>> chain = result.getPrimaryActionChainAsMap();
        assertEquals(1, chain.size());
        assertNotNull(chain.get(1));
        assertTrue(chain.get(1).isEmpty());
    }

    @Test
    void shouldCopyActionKeyListDefensively() {
        EvaluatorResult result = new EvaluatorResult();
        EvaluatorResult.ChainElement element = new EvaluatorResult.ChainElement();
        element.setOrder(1);
        List<String> keys = new ArrayList<>(List.of("a"));
        element.setActionKeys(keys);
        result.setPrimaryActionChain(List.of(element));

        Map<Integer, List<String>> chain = result.getPrimaryActionChainAsMap();
        keys.add("b");

        assertEquals(new LinkedHashMap<>(Map.of(1, List.of("a"))), new LinkedHashMap<>(chain));
    }
}
