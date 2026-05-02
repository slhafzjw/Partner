import work.slhaf.partner.Main;
import work.slhaf.partner.framework.agent.Agent;

public class TestMain {
    public static void main(String[] args) {
        Agent.newAgent(Main.class).launch();
    }
}
