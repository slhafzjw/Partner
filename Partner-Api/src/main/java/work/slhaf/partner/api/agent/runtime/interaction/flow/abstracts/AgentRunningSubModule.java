package work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts;


/**
 * 流程子模块基类
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public abstract class AgentRunningSubModule<I, O> extends Module {

    public abstract O execute(I data);

}
