package work.slhaf.partner.module.impression;

import java.util.List;

public record ImpressionUpdateApplyResult(
        List<String> createdEntityUuids
) {

    public ImpressionUpdateApplyResult {
        createdEntityUuids = createdEntityUuids == null ? List.of() : List.copyOf(createdEntityUuids);
    }

    public static ImpressionUpdateApplyResult empty() {
        return new ImpressionUpdateApplyResult(List.of());
    }
}
