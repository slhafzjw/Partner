package work.slhaf.partner.module.modules.action.interventor;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.val;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.common.module.PreRunningAbstractAgentModuleAbstract;
import work.slhaf.partner.module.modules.action.interventor.entity.InterventionType;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;
import work.slhaf.partner.module.modules.action.interventor.evaluator.InterventionEvaluator;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorResult.EvaluatedInterventionData;
import work.slhaf.partner.module.modules.action.interventor.recognizer.InterventionRecognizer;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerInput;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 负责识别潜在的行动干预信息，作用于正在进行或已存在的行动池中内容
 */
public class ActionInterventor extends PreRunningAbstractAgentModuleAbstract implements ActivateModel {
    private final AssemblyHelper assemblyHelper = new AssemblyHelper();
    private final PromptHelper promptHelper = new PromptHelper();
    /**
     * 键: 本次调用uuid；
     * 值：本次调用对应的prompt；
     */
    private final Map<String, Map<String, String>> interventionPrompt = new HashMap<>();
    @InjectModule
    private InterventionRecognizer interventionRecognizer;
    @InjectModule
    private InterventionEvaluator interventionEvaluator;
    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

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
            // 同步写入prompt，异步处理干预行为，‘异步’在处理流程中体现
            promptHelper.setupInterventionPrompt(uuid, executingDataList, preparedDataList);
            handleInterventions(executingDataList, recognizerResult.getExecutingInterventions());
            handleInterventions(preparedDataList, recognizerResult.getPreparedInterventions());
        } else {
            promptHelper.setupInterventionIgnoredPrompt(uuid, executingDataList, preparedDataList);
        }
    }

    private void handleInterventions(List<EvaluatedInterventionData> interventionDataList, Map<String, ExecutableAction> interventionDataMap) {
        val executor = actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM);
        executor.execute(() -> {
            for (EvaluatedInterventionData interventionData : interventionDataList) {
                // 此处拿到的为 ActionData 或者 PhaserRecord, 来自 Recognizer 的封装
                val data = interventionDataMap.get(interventionData.getTendency());
                actionCapability.handleInterventions(interventionData.getMetaInterventionList(), data);
            }
        });
    }

    private void invalidActionKeysFilter(List<EvaluatedInterventionData> interventions) {
        List<EvaluatedInterventionData> toRemove = new ArrayList<>();
        for (EvaluatedInterventionData intervention : interventions) {
            List<MetaIntervention> interventionData = intervention.getMetaInterventionList();
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

    @Override
    public int order() {
        return 2;
    }

    private final class AssemblyHelper {
        private AssemblyHelper() {
        }

        private RecognizerInput buildRecognizerInput(String userId, String input) {
            RecognizerInput recognizerInput = new RecognizerInput();
            recognizerInput.setInput(input);
            recognizerInput.setUserDialogMapStr(memoryCapability.getUserDialogMapStr(userId));
            // 参考的对话列表大小或需调整
            recognizerInput.setRecentMessages(cognationCapability.getChatMessages());
            recognizerInput.setExecutingActions(actionCapability.listPhaserRecords().stream().map(PhaserRecord::executableAction).toList());
            recognizerInput.setPreparedActions(actionCapability.listActions(ExecutableAction.Status.PREPARE, userId).stream().toList());
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
                for (MetaIntervention intervention : data.getMetaInterventionList()) {
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
