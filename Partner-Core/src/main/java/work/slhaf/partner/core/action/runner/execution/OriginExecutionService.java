package work.slhaf.partner.core.action.runner.execution;

import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;
import work.slhaf.partner.core.action.runner.policy.WrappedLaunchSpec;

import java.io.File;
import java.util.Arrays;

import static work.slhaf.partner.core.action.ActionCore.ORIGIN_LOCATION;

public class OriginExecutionService {

    public OriginExecutionService() {
    }

    public RunnerClient.RunnerResponse run(MetaAction metaAction) {
        RunnerClient.RunnerResponse response = new RunnerClient.RunnerResponse();
        File file = new File(resolveOriginPath(metaAction));
        String[] commands = CommandExecutionService.INSTANCE.buildFileExecutionCommands(metaAction.getLauncher(), metaAction.getParams(), file.getAbsolutePath());
        WrappedLaunchSpec wrapped = ExecutionPolicyRegistry.INSTANCE.prepare(Arrays.stream(commands).toList());
        CommandExecutionService.Result execResult = CommandExecutionService.INSTANCE.exec(wrapped);
        response.setOk(execResult.isOk());
        response.setData(execResult.getTotal());
        return response;
    }

    private String resolveOriginPath(MetaAction metaAction) {
        if (ORIGIN_LOCATION.equals(metaAction.getLocation())) {
            return metaAction.getName();
        }
        return metaAction.getLocation();
    }
}
