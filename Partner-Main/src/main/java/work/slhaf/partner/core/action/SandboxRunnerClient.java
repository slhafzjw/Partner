package work.slhaf.partner.core.action;

import work.slhaf.partner.core.action.entity.MetaAction;

import java.nio.file.Path;

/**
 * 基于 Http 与 WebSocket 的沙盒执行器客户端，负责:
 * <ul>
 *     <li>
 *         发送行动单元数据
 *     </li>
 *     <li>
 *         实时更新获取已存在行动列表
 *     </li>
 *     <li>
 *         向传入的 MetaAction 回写执行结果
 *     </li>
 * </ul>
 */
class SandboxRunnerClient {

    public SandboxRunnerClient() {
        // 连接沙盒执行器(websocket)
    }

    public void run(MetaAction metaAction) {
        // 获取已存在行动列表
        Path path = metaAction.checkAndGetPath();
        if (!metaAction.getResult().isSuccess()) {
            return;
        }
        // 调用沙盒执行器
    }

}
