package work.slhaf.partner.core.action.entity;

import work.slhaf.partner.core.action.entity.ExecutableAction.Status;

import java.util.concurrent.Phaser;

public record PhaserRecord(Phaser phaser, ExecutableAction executableAction) {

    public void fail() {
        executableAction.setStatus(Status.FAILED);
    }

    /**
     * 负责将 ActionData 的状态设置为 INTERRUPTED
     * 同时循环检查进行阻塞
     */
    public void interrupt() {
        executableAction.setStatus(Status.INTERRUPTED);
        while (executableAction().getStatus() == Status.INTERRUPTED) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * 将状态重新设置为 EXECUTING ，恢复 interrupt 阻塞状态
     */
    public void complete() {
        executableAction().setStatus(Status.EXECUTING);
    }
}
