package work.slhaf.partner.core.action.entity;

import work.slhaf.partner.core.action.entity.ActionData.ActionStatus;

import java.util.concurrent.Phaser;

public record PhaserRecord(Phaser phaser, ActionData actionData) {

    public void fail() {
        actionData.setStatus(ActionStatus.FAILED);
    }

    public void interrupt() {

    }

    public void complete() {

    }
}
