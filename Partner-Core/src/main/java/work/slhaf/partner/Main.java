package work.slhaf.partner;

import work.slhaf.partner.framework.agent.Agent;

public class Main {
    public static void main(String[] args) {
        boolean launched = Agent.newAgent(Main.class).launch();
        if (!launched) {
            System.exit(1);
        }
    }
}
