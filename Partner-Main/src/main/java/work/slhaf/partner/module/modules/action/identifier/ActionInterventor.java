package work.slhaf.partner.module.modules.action.identifier;

import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore.PhaserRecord;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.action.identifier.evaluator.InterventionEvaluator;
import work.slhaf.partner.module.modules.action.identifier.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.identifier.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.identifier.evaluator.entity.EvaluatorResult.EvaluatedInterventionData;
import work.slhaf.partner.module.modules.action.identifier.handler.InterventionHandler;
import work.slhaf.partner.module.modules.action.identifier.handler.entity.HandlerInput;
import work.slhaf.partner.module.modules.action.identifier.handler.entity.HandlerInput.HandlerInputData;
import work.slhaf.partner.module.modules.action.identifier.recognizer.InterventionRecognizer;
import work.slhaf.partner.module.modules.action.identifier.recognizer.entity.RecognizerInput;
import work.slhaf.partner.module.modules.action.identifier.recognizer.entity.RecognizerResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 负责识别潜在的行动干预信息，作用于正在进行或已存在的行动池中内容
 */
@AgentModule(name = "action_identifier", order = 2)
public class ActionInterventor extends PreRunningModule implements ActivateModel {

    @InjectModule
    private InterventionRecognizer interventionRecognizer;
    @InjectModule
    private InterventionEvaluator interventionEvaluator;
    @InjectModule
    private InterventionHandler InterventionHandler;

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

    /**
     * 键: 本次调用uuid；
     * 值：本次调用对应的prompt；
     */
    private Map<String, HashMap<String, String>> interventionPrompt = new HashMap<>();

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {
        // 综合当前正在进行的行动链信息、用户交互历史、激活的记忆切片，尝试识别出是否存在行动干预意图
        // 首先通过recognizer进行快速意图识别，识别成功则步入评估阶段，评估成功则直接作用于目标行动链
        // 进行快速意图识别时必须结合近期对话与进行中行动链情况
        String uuid = context.getUuid();
        String userId = context.getUserId();
        RecognizerResult recognizerResult = interventionRecognizer
                .execute(buildRecognizerInput(userId, context.getInput()));
        if (!recognizerResult.isOk()) {
            // 设置相应prompt
            setupNoInterventionPrompt(uuid);
            return;
        }
        // 存在则进一步评估、评估通过则并直接添加行动程序至对应行动链
        Map<String, PhaserRecord> recognizedInterventions = recognizerResult.getInterventions();
        EvaluatorResult evaluatorResult = interventionEvaluator
                .execute(buildEvaluatorInput(recognizedInterventions.keySet(), userId));
        List<EvaluatedInterventionData> interventions = evaluatorResult.getDataList();
        if (evaluatorResult.isOk()) {
            // 同步写入prompt，异步处理干预行为
            setupInterventionPrompt(uuid, interventions);
            InterventionHandler.execute(buildHandlerInput(interventions));
        } else {
            // 同步写入prompt
            setupInterventionIgnoredPrompt(uuid, interventions);
        }
    }

    private HandlerInput buildHandlerInput(List<EvaluatedInterventionData> interventions) {
        HandlerInput input = new HandlerInput();
        List<HandlerInputData> inputDataList = input.getData();
        for(EvaluatedInterventionData interventionData: interventions){
            HandlerInputData inputData = new HandlerInputData();
            inputData.setTendency(interventionData.getTendency());
            inputData.setDescription(interventionData.getDescription());
            inputData.setType(interventionData.getType());
            inputData.setActions(interventionData.getActions());
            inputDataList.add(inputData);
        }
        return input;
    }

    private void setupInterventionIgnoredPrompt(String uuid, List<EvaluatedInterventionData> dataList) {

    }

    private void setupInterventionPrompt(String uuid, List<EvaluatedInterventionData> dataList) {

    }

    private void setupNoInterventionPrompt(String uuid) {

    }

    private EvaluatorInput buildEvaluatorInput(Set<String> interventionTendencies, String userId) {
        EvaluatorInput input = new EvaluatorInput();
        input.setInterventionTendencies(interventionTendencies);
        input.setRecentMessages(cognationCapability.getChatMessages());
        input.setActivatedSlices(memoryCapability.getActivatedSlices(userId));
        return input;
    }

    private RecognizerInput buildRecognizerInput(String userId, String input) {
        RecognizerInput recognizerInput = new RecognizerInput();
        recognizerInput.setInput(input);
        recognizerInput.setUserDialogMapStr(memoryCapability.getUserDialogMapStr(userId));
        // 参考的对话列表大小或需调整
        recognizerInput.setRecentMessages(cognationCapability.getChatMessages());
        recognizerInput.setExecutingActions(actionCapability.listPhaserRecords());
        return recognizerInput;
    }

    @Override
    public String modelKey() {
        return "action_identifier";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        return interventionPrompt.get(context.getUuid());
    }

    @Override
    protected String moduleName() {
        return "[行动干预识别模块]";
    }
}
