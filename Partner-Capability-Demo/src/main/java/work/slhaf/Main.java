package work.slhaf;

import work.slhaf.demo.TestModule;
import work.slhaf.demo.capability.CapabilityRegisterFactory;

public class Main {
    public static void main(String[] args) throws ClassNotFoundException {
        TestModule testModule = new TestModule();
        CapabilityRegisterFactory.getInstance().registerCapabilities(Main.class.getPackage().getName());
        testModule.execute();
    }
}