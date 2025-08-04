package work.slhaf.partner.api.factory.context;

import lombok.Data;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.net.URL;
import java.util.List;

@Data
public class AgentRegisterContext {
    private Reflections reflections;
    private CapabilityFactoryContext capabilityFactoryContext = new CapabilityFactoryContext();
    private ConfigFactoryContext configFactoryContext = new ConfigFactoryContext();
    private ModuleFactoryContext moduleFactoryContext = new ModuleFactoryContext();

    public AgentRegisterContext(List<URL> urls) {
        reflections = new Reflections(new ConfigurationBuilder().setScanners(
                        Scanners.FieldsAnnotated,
                        Scanners.SubTypes,
                        Scanners.MethodsAnnotated,
                        Scanners.TypesAnnotated
                )
                .setUrls(urls)
        );
    }
}
