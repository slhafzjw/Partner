package work.slhaf.partner.api.factory.config;

import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;

public class ConfigLoaderFactory extends AgentBaseFactory {

    @Override
    protected void setVariables(AgentRegisterContext context) {

    }

    @Override
    protected void run() {
        //反射获取是否存在其他的ModelConfigFactory子类，如果存在，则不使用默认配置工厂
        ModelConfigFactory factory;
        if (){

        }
        factory.load();
    }
}
