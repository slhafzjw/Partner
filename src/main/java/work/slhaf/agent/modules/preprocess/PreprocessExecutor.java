package work.slhaf.agent.modules.preprocess;

import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.interaction.data.InteractionInputData;

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

        context.setUserInfo(inputData.getUserInfo());
        context.setUserNickname(inputData.getUserNickName());
        context.setDateTime(inputData.getLocalDateTime());

        context.setFinished(false);
        context.setInput(inputData.getContent());

        return context;
    }
}
