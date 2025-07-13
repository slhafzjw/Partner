package work.slhaf.agent.core.cognation.submodule.dispatch;

import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.cognation.submodule.dispatch.pojo.DispatchData;

import java.io.Serial;

public class DispatchCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;
    public static volatile DispatchCore dispatchCore;

    public static DispatchCore getInstance() {
        if (dispatchCore == null) {
            synchronized (DispatchCore.class) {
                if (dispatchCore == null) {
                    dispatchCore = new DispatchCore();
                }
            }
        }
        return dispatchCore;
    }

    public void dispatch(DispatchData dispatchData){

    }

    public void listDispatchData(){

    }
}
