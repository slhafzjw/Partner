package work.slhaf.agent.modules.preprocess;

import work.slhaf.agent.core.interation.data.InteractionInputData;
import work.slhaf.module.InteractionContext;

public class PreprocessExecutor {

    private static PreprocessExecutor preprocessExecutor;

    private PreprocessExecutor(){}

    public static PreprocessExecutor getInstance() {
        if (preprocessExecutor == null) {
            preprocessExecutor = new PreprocessExecutor();
        }
        return preprocessExecutor;
    }

    public InteractionContext execute(InteractionInputData inputData) {
        InteractionContext context = new InteractionContext();
        context.setDateTime(inputData.getLocalDateTime());
        context.setFinished(false);
        context.setInput(inputData.getContent());
        context.setUserInfo(inputData.getUserInfo());
        context.setUserNickname(inputData.getUserNickName());

        return context;
    }
}
