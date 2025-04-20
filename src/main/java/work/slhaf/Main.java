package work.slhaf;

import work.slhaf.agent.Agent;
import work.slhaf.agent.core.interaction.data.InteractionInputData;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Agent agent = Agent.initialize();

        InteractionInputData inputData = new InteractionInputData();
        inputData.setContent("hello");
        inputData.setPlatform("cli");
        inputData.setUserInfo("owner");
        inputData.setUserNickName("master");

        agent.receiveUserInput(inputData);
    }
}