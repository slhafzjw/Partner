package work.slhaf.partner.core.action.entity;

import java.util.concurrent.Phaser;

public record PhaserRecord(Phaser phaser, ActionData actionData) {

    public void fail() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fail'");
    }


    public void interrupt() {

    }

    public void complete() {

    }
}
