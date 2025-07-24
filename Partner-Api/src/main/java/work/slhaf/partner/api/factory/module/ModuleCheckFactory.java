package work.slhaf.partner.api.factory.module;

import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;

public class ModuleCheckFactory extends AgentBaseFactory {
    @Override
    protected void setVariables(AgentRegisterContext context) {

    }

    @Override
    protected void run() {
        //检查注解AgentModule所在类是否继承了AgentInteractionModule
        //检查hook注解所在方法是否位于AgentInteractionModule子类/AgentInteractionSubModule子类/ActivateModel子类
    }
}
