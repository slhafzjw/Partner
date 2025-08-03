package work.slhaf.partner.api.factory.module;

import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;

import java.lang.reflect.InvocationTargetException;

/**
 * 负责执行前hook逻辑
 */
public class ModulePreHookExecuteFactory extends AgentBaseFactory {
    @Override
    protected void setVariables(AgentRegisterContext context) {

    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

    }
}
