package work.slhaf.partner.module.action.builtin;

import java.util.List;

public interface BuiltinActionProvider {
    List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions();

    String createActionKey(String actionName);

    default String param(String requirement, String type, String detail) {
        return requirement + "|" + type + "|" + detail;
    }

}
