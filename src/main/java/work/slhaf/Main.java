package work.slhaf;

import work.slhaf.agent.Agent;
import work.slhaf.agent.modules.memory.MemoryGraph;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Agent agent = Agent.initialize();
        agent.receiveUserInput("111","222","hello");
    }
}