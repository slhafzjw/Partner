package work.slhaf.partner.api.factory.module;

import org.reflections.Reflections;
import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;

public class ModuleRegisterFactory extends AgentBaseFactory {

    private Reflections reflections;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        reflections = context.getReflections();
    }

    @Override
    protected void run() {
        //反射扫描获取InteractionModule所在类与hook注解所在方法
    }
}
