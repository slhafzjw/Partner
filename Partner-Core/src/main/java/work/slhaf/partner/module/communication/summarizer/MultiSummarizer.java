package work.slhaf.partner.module.communication.summarizer;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MultiSummarizer extends AbstractAgentModule.Sub<List<Message>, Result<String>> implements ActivateModel {

    @InjectModule
    private MemoryRuntime memoryRuntime;

    @Override
    protected @NotNull Result<String> doExecute(List<Message> messages) {
        return chat(
                List.of(new Message(Message.Character.USER, JSONUtil.toJsonPrettyStr(messages)))
        );
    }

    @NotNull
    @Override
    public String modelKey() {
        return "multi_summarizer";
    }
}
