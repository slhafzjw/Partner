package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 基于 Http 与 WebSocket 的沙盒执行器客户端，负责:
 * <ul>
 * <li>
 * 发送行动单元数据
 * </li>
 * <li>
 * 实时更新获取已存在行动列表
 * </li>
 * <li>
 * 向传入的 MetaAction 回写执行结果
 * </li>
 * </ul>
 */
public class SandboxRunnerClient extends RunnerClient {

    @Override
    protected Path doBuildTempPath(MetaAction tempAction, String codeType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void doSerialize(MetaAction tempAction, String code, String codeType) {
        // TODO Auto-generated method stub

    }

    public SandboxRunnerClient(Map<String, MetaActionInfo> existedMetaActions, ExecutorService executor) { // 连接沙盒执行器(websocket)
        super(existedMetaActions, executor);
    }

    public RunnerResponse doRun(MetaAction metaAction) {
        // 调用沙盒执行器
        return null;
    }

    @Override
    public JSONObject listSysDependencies() {
        // TODO Auto-generated method stub
        return null;
    }

}
