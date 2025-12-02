package work.slhaf.partner.module.modules.action.interventor;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.action.interventor.entity.InterventionType;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;
import work.slhaf.partner.module.modules.action.interventor.evaluator.InterventionEvaluator;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorResult.EvaluatedInterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.InterventionHandler;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.ExecutingInterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.InterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.PreparedInterventionData;
import work.slhaf.partner.module.modules.action.interventor.recognizer.InterventionRecognizer;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerInput;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
    private InterventionHandler interventionHandler;

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

    private final AssemblyHelper assemblyHelper = new AssemblyHelper();
    private final PromptHelper promptHelper = new PromptHelper();

    /**
     * 键: 本次调用uuid；
     * 值：本次调用对应的prompt；
     */
    private final Map<String, Map<String, String>> interventionPrompt = new HashMap<>();

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {
        // 综合当前正在进行的行动链信息、用户交互历史、激活的记忆切片，尝试识别出是否存在行动干预意图
        // 首先通过recognizer进行快速意图识别，识别成功则步入评估阶段，评估成功则直接作用于目标行动链
        // 进行快速意图识别时必须结合近期对话与进行中行动链情况

        // 干预意图识别
        String uuid = context.getUuid();
        String userId = context.getUserId();
        RecognizerResult recognizerResult = interventionRecognizer
                .execute(assemblyHelper.buildRecognizerInput(userId, context.getInput())); // 此处的输入内容携带了所有 PhaserRecord
        if (!recognizerResult.isOk()) {
            promptHelper.setupNoInterventionPrompt(uuid);
            return;
        }

        // 干预意图评估
        EvaluatorResult evaluatorResult = interventionEvaluator
                .execute(assemblyHelper.buildEvaluatorInput(recognizerResult, userId));
        List<EvaluatedInterventionData> executingDataList = evaluatorResult.getExecutingDataList();
        List<EvaluatedInterventionData> preparedDataList = evaluatorResult.getPreparedDataList();

        // 意图评估结果处理
        if (evaluatorResult.isOk()) {
            // 对存在‘异常ActionKey’的评估结果列表进行过滤
            invalidActionKeysFilter(executingDataList);
            invalidActionKeysFilter(preparedDataList);

            // 同步写入prompt，异步处理干预行为，‘异步’在 interventionHandler 中体现
            promptHelper.setupInterventionPrompt(uuid, executingDataList, preparedDataList);
            interventionHandler.execute(assemblyHelper.buildHandlerInput(executingDataList, preparedDataList, recognizerResult));
        } else {
            promptHelper.setupInterventionIgnoredPrompt(uuid, executingDataList, preparedDataList);
        }

    }

    private void invalidActionKeysFilter(List<EvaluatedInterventionData> interventions) {

        List<EvaluatedInterventionData> toRemove = new ArrayList<>();

        for (EvaluatedInterventionData intervention : interventions) {
            List<MetaIntervention> interventionData = intervention.getInterventionData();
            List<String> actions = new ArrayList<>();
            for (MetaIntervention metaData : interventionData) {
                actions.addAll(metaData.getActions());
            }
            // 如果存在异常行动key，则可视为该评估结果存在问题，直接忽略该结果
            if (!actionCapability.checkExists(actions.toArray(String[]::new))) {
                toRemove.add(intervention);
            }

            // 针对 REBUILD 类型进行特殊校验, REBUILD 类型必须满足所有 MetaIntervention 的类型均为 REBUILD
            if (!checkRebuildType(interventionData)) {
                toRemove.add(intervention);
            }
        }

        interventions.removeAll(toRemove);
    }

    private boolean checkRebuildType(List<MetaIntervention> interventionData) {
        boolean hasRebuild = false;
        for (MetaIntervention meta : interventionData) {
            if (meta.getType() == InterventionType.REBUILD) {
                hasRebuild = true;
            } else if (hasRebuild) {
                // 已经存在REBUILD类型，但又发现了非REBUILD类型，不合法
                return false;
            }
        }

        return true;
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
    protected Map<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        return interventionPrompt.remove(context.getUuid());
    }

    @Override
    protected String moduleName() {
        return "[行动干预识别模块]";
    }

    private final class AssemblyHelper {
        private AssemblyHelper() {
        }

        /**
         * @param executingDataList 对应评估结果中的‘执行中行动’
         * @param preparedDataList  对应评估结果中的‘待执行行动’
         * @param recognizerResult  干预识别结果，包含‘执行中’‘待执行’两类评估结果各自对应的行动数据
         * @return 处理器输入
         */
        private HandlerInput buildHandlerInput(List<EvaluatedInterventionData> executingDataList,
                                               List<EvaluatedInterventionData> preparedDataList, RecognizerResult recognizerResult) {
            HandlerInput input = new HandlerInput();
            Map<String, PhaserRecord> executingInterventions = recognizerResult.getExecutingInterventions();
            Map<String, ActionData> preparedInterventions = recognizerResult.getPreparedInterventions();

            List<ExecutingInterventionData> executing = setupInputDataList(executingDataList, executingInterventions,
                    ExecutingInterventionData::new);
            List<PreparedInterventionData> prepared = setupInputDataList(preparedDataList, preparedInterventions,
                    PreparedInterventionData::new);

            input.setExecuting(executing);
            input.setPrepared(prepared);

            return input;
        }

        /**
         * @param <I>               HandlerInput 中 List 对应的泛型
         * @param evaluatedDataList 评估结果列表
         * @param interventionMap   干预识别结果中的 tendency:data 映射
         * @param factory           输入类型构建工厂
         * @return 处理器输入(干预列表)
         */
        private <I> List<I> setupInputDataList(List<EvaluatedInterventionData> evaluatedDataList,
                                               Map<String, ?> interventionMap, Supplier<I> factory) {

            List<I> result = new ArrayList<>();

            for (EvaluatedInterventionData interventionData : evaluatedDataList) {

                I data = factory.get();

                if (data instanceof InterventionData inputData) {
                    inputData.setTendency(interventionData.getTendency());
                    inputData.setDescription(interventionData.getDescription());
                    inputData.setInterventions(interventionData.getInterventionData());
                }

                if (data instanceof ExecutingInterventionData inputData) {
                    inputData.setRecord((PhaserRecord) interventionMap.get(interventionData.getTendency()));
                } else if (data instanceof PreparedInterventionData inputData) {
                    inputData.setActionData((ActionData) interventionMap.get(interventionData.getTendency()));
                }

                result.add(data);
            }

            return result;
        }


        private RecognizerInput buildRecognizerInput(String userId, String input) {
            RecognizerInput recognizerInput = new RecognizerInput();
            recognizerInput.setInput(input);
            recognizerInput.setUserDialogMapStr(memoryCapability.getUserDialogMapStr(userId));
            // 参考的对话列表大小或需调整
            recognizerInput.setRecentMessages(cognationCapability.getChatMessages());
            recognizerInput.setExecutingActions(actionCapability.listPhaserRecords());
            recognizerInput.setPreparedActions(actionCapability.listPreparedAction(userId));
            return recognizerInput;
        }

        private EvaluatorInput buildEvaluatorInput(RecognizerResult recognizerResult, String userId) {
            EvaluatorInput input = new EvaluatorInput();
            input.setExecutingInterventions(recognizerResult.getExecutingInterventions());
            input.setPreparedInterventions(recognizerResult.getPreparedInterventions());
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setActivatedSlices(memoryCapability.getActivatedSlices(userId));
            return input;
        }
    }

    private final class PromptHelper {
        private PromptHelper() {
        }

        private void setupInterventionIgnoredPrompt(String uuid, List<EvaluatedInterventionData> executingDataList, List<EvaluatedInterventionData> preparedDataList) {
            List<EvaluatedInterventionData> total = Stream.concat(executingDataList.stream(), preparedDataList.stream()).toList();

            JSONArray reasons = new JSONArray();

            for (EvaluatedInterventionData data : total) {
                JSONObject reason = reasons.addObject();
                reason.put("[干预倾向]", data.getTendency());
                reason.put("[未采用原因]", data.getDescription());
            }

            synchronized (interventionPrompt) {
                interventionPrompt.put(uuid, Map.of(
                        "[识别状态] <是否识别到干预已存在行动的意图>", "识别到，但都未采用",
                        "[忽略原因] <各个意图被忽略的原因>", reasons.toString(),
                        "[干预行动] <将对已存在行动做出的行为>", "无行为"));
            }
        }

        private void setupInterventionPrompt(String uuid, List<EvaluatedInterventionData> executingDataList,
                                             List<EvaluatedInterventionData> preparedDataList) {
            JSONArray contents = new JSONArray();
            List<EvaluatedInterventionData> temp = Stream.concat(executingDataList.stream(), preparedDataList.stream()).toList();

            for (EvaluatedInterventionData data : temp) {
                if (!data.isOk()) {
                    continue;
                }
                String tendency = data.getTendency();
                JSONObject newElement = contents.addObject();
                newElement.put("[干预倾向]", tendency);
                JSONArray changes = newElement.putArray("[行动链变动情况]");

                for (MetaIntervention intervention : data.getInterventionData()) {
                    JSONObject change = changes.addObject();
                    change.put("[干预类型]", intervention.getType());
                    change.put("[干预序号]", intervention.getOrder());
                    change.putArray("[干预内容]").addAll(intervention.getActions());
                }
            }

            synchronized (interventionPrompt) {
                interventionPrompt.put(uuid, Map.of(
                        "[识别状态] <是否识别到干预已存在行动的意图>", "识别到，将采用",
                        "[干预内容] <将对已存在行动做出的行为>", contents.toString()));
            }
        }

        private void setupNoInterventionPrompt(String uuid) {
            interventionPrompt.put(uuid, Map.of(
                    "[识别状态] <是否识别到干预已存在行动的意图>", "未识别到干预意图",
                    "[干预行动] <将对已存在行动做出的行为>", "无行动"));
        }
    }
}
