package work.slhaf.partner.module.modules.action.builtin;

import java.util.List;

public interface BuiltinActionProvider {
    List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions();
}
