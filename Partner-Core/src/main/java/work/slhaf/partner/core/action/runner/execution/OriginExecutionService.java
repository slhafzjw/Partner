package work.slhaf.partner.core.action.runner.execution;

import cn.hutool.core.io.FileUtil;
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
        String ext = FileUtil.getSuffix(file);
        if (ext == null || ext.isEmpty()) {
            response.setOk(false);
            response.setData("未知文件类型");
            return response;
        }
        String[] commands = commandExecutionService.buildCommands(ext, metaAction.getParams(), file.getAbsolutePath());
        if (commands == null || commands.length == 0) {
            response.setOk(false);
            response.setData("不支持的文件类型: " + file.getName());
            return response;
        }
        CommandExecutionService.Result execResult = commandExecutionService.exec(commands);
        response.setOk(execResult.isOk());
        response.setData(execResult.getTotal());
        return response;
    }
}
