package work.slhaf.partner.runtime.interaction;

import work.slhaf.partner.api.agent.runtime.interaction.AgentInteractionAdapter;
import work.slhaf.partner.runtime.interaction.data.PartnerInputData;
import work.slhaf.partner.runtime.interaction.data.PartnerOutputData;
import work.slhaf.partner.runtime.interaction.data.SpecializedPartnerInputData;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

public class PartnerInteractionAdapter extends AgentInteractionAdapter<PartnerInputData, PartnerOutputData, PartnerRunningFlowContext> {
    @Override
    protected PartnerOutputData parseOutputData(PartnerRunningFlowContext outputContext) {
        PartnerOutputData outputData = new PartnerOutputData();
        outputData.setCode(outputContext.getOk());
        outputData.setContent(getContent(outputContext));
        outputData.setUserInfo(outputContext.getUserInfo());
        outputData.setDateTime(outputContext.getDateTime());
        return outputData;
    }

    private String getContent(PartnerRunningFlowContext outputContext) {
        StringBuilder str = new StringBuilder();
        str.append(outputContext.getCoreResponse().getString("text")).append("\r\n")
                .append("\r\n错误信息:\r\n")
                .append(outputContext.getErrMsg().toString());
        return str.toString();
    }

    @Override
    protected PartnerRunningFlowContext parseInputData(PartnerInputData inputData) {
        PartnerRunningFlowContext context = new PartnerRunningFlowContext();
        context.setUserNickname(inputData.getUserNickName());
        context.setUserInfo(inputData.getUserInfo());
        context.setDateTime(inputData.getDateTime());
        context.setSingle(inputData.isSingle());
        context.setPlatform(inputData.getPlatform());
        context.setInput(inputData.getContent());
        context.setType(inputData.getInputType());
        if (inputData instanceof SpecializedPartnerInputData specializedData) {
            context.setPayload(specializedData.getPayload());
        }
        return context;
    }
}
