package work.slhaf.partner.core.action.runner.execution;

import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;
import work.slhaf.partner.core.action.runner.policy.WrappedLaunchSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OriginExecutionService {

    public OriginExecutionService() {
    }

    public RunnerClient.RunnerResponse run(MetaAction metaAction) {
        RunnerClient.RunnerResponse response = new RunnerClient.RunnerResponse();
        File file = new File(metaAction.getLocation());
        String[] commands = CommandExecutionService.INSTANCE.buildFileExecutionCommands(metaAction.getLauncher(), metaAction.getParams(), file.getAbsolutePath());
        WrappedLaunchSpec wrapped = ExecutionPolicyRegistry.INSTANCE.prepare(Arrays.stream(commands).toList());
        List<String> wrappedCommands = new ArrayList<>();
        wrappedCommands.add(wrapped.getCommand());
        wrappedCommands.addAll(wrapped.getArgs());
        CommandExecutionService.Result execResult = CommandExecutionService.INSTANCE.exec(wrappedCommands);
        response.setOk(execResult.isOk());
        response.setData(execResult.getTotal());
        return response;
    }
}
