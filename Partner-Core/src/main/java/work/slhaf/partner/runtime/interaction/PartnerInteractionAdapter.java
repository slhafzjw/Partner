package work.slhaf.partner.runtime.interaction;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.runtime.interaction.AgentInteractionAdapter;
import work.slhaf.partner.runtime.interaction.data.PartnerInputData;
import work.slhaf.partner.runtime.interaction.data.PartnerOutputData;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.ZonedDateTime;

public class PartnerInteractionAdapter extends AgentInteractionAdapter<PartnerInputData, PartnerOutputData, PartnerRunningFlowContext> {
    @NotNull
    @Override
    protected PartnerOutputData parseOutputData(PartnerRunningFlowContext outputContext) {
        PartnerOutputData outputData = new PartnerOutputData();
        outputData.setCode(outputContext.getStatus().getOk() ? 1 : 0);
        outputData.setContent(getContent(outputContext));
        outputData.setUserInfo(outputContext.getTarget());
        outputData.setDateTime(ZonedDateTime.now().toLocalDateTime());
        return outputData;
    }

    private String getContent(PartnerRunningFlowContext outputContext) {
        StringBuilder str = new StringBuilder();
        str.append(outputContext.getCoreResponse().getString("text"));
        if (!outputContext.getStatus().getOk()) {
            str.append("\r\n").append("\r\n错误信息:\r\n").append(outputContext.getStatus().getErrors());
        }
        return str.toString();
    }

    @NotNull
    @Override
    protected PartnerRunningFlowContext parseInputData(PartnerInputData inputData) {
        return new PartnerRunningFlowContext(
                inputData.getUserInfo(),
                inputData.getContent(),
                inputData.getPlatform(),
                inputData.getUserNickName()
        );
    }
}
