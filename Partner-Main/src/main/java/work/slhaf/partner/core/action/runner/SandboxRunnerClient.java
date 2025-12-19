package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
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

    public SandboxRunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor) { // 连接沙盒执行器(websocket)
        super(existedMetaActions, executor);
    }

    protected RunnerResponse doRun(MetaAction metaAction) {
        // 调用沙盒执行器
        return null;
    }

    @Override
    public JSONObject listSysDependencies() {
        return null;
    }

    @Override
    public String buildTmpPath(MetaAction tempAction, String codeType) {
        throw new UnsupportedOperationException("Unimplemented method 'buildTmpPath'");
    }

    @Override
    public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'tmpSerialize'");
    }

    @Override
    public void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData) {
        throw new UnsupportedOperationException("Unimplemented method 'persistSerialize'");
    }

}
