package work.slhaf.partner.module.action.builtin;

import java.util.List;

interface BuiltinActionProvider {
    List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions();

    String createActionKey(String actionName);
}
