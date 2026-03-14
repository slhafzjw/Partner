package work.slhaf.partner.core.action.runner.execution;

import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.runner.RunnerClient;

import java.io.File;

public class OriginExecutionService {

    private final CommandExecutionService commandExecutionService;

    public OriginExecutionService(CommandExecutionService commandExecutionService) {
        this.commandExecutionService = commandExecutionService;
    }

    public RunnerClient.RunnerResponse run(MetaAction metaAction) {
        RunnerClient.RunnerResponse response = new RunnerClient.RunnerResponse();
        File file = new File(metaAction.getLocation());
        String[] commands = commandExecutionService.buildCommands(metaAction.getLauncher(), metaAction.getParams(), file.getAbsolutePath());
        CommandExecutionService.Result execResult = commandExecutionService.exec(commands);
        response.setOk(execResult.isOk());
        response.setData(execResult.getTotal());
        return response;
    }
}
