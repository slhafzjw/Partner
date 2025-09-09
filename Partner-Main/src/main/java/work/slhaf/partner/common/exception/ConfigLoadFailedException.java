package work.slhaf.partner.common.exception;

import work.slhaf.partner.api.agent.factory.config.exception.ConfigFactoryInitFailedException;

public class ConfigLoadFailedException extends ConfigFactoryInitFailedException {
    public ConfigLoadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigLoadFailedException(String message) {
        super(message);
    }
}
