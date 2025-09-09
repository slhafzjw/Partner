package work.slhaf.partner.core.submodule.dispatch;

import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.core.submodule.dispatch.pojo.DispatchData;

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
