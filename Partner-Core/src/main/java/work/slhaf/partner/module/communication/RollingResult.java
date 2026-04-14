package work.slhaf.partner.module.communication;

import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.List;

public record RollingResult(
        MemoryUnit memoryUnit,
        MemorySlice memorySlice,
        List<Message> incrementMessages,
        String summary,
        int rollingSize,
        int retainDivisor
) {
}
