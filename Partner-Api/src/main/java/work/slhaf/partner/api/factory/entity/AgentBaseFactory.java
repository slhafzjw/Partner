package work.slhaf.partner.api.factory.entity;

import work.slhaf.partner.api.factory.capability.exception.FactoryExecuteFailedException;

import java.lang.reflect.InvocationTargetException;

public abstract class AgentBaseFactory {
    public void execute(AgentRegisterContext context) {
        try {
            setVariables(context);
            run();
        } catch (Exception e) {
            throw new FactoryExecuteFailedException(e.getMessage(), e);
        }
    }

    protected abstract void setVariables(AgentRegisterContext context);

    protected abstract void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;
}
