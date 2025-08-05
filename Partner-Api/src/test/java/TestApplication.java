import work.slhaf.partner.api.Agent;

public class TestApplication {
    public static void main(String[] args) {
        Agent.newAgent(TestApplication.class,null).run();
    }
}
