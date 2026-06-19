package work.slhaf.partner.module.impression;

import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.List;

public record ImpressionUpdateContext(
        String memoryUnitId,
        String memorySliceId,
        String summary,
        int rollingSize,
        int retainDivisor,
        int sliceStartIndex,
        int sliceEndIndex,
        long sliceTimestamp,
        long unitTimestamp,
        List<Message> incrementMessages
) {
}
