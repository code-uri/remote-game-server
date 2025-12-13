package aimlabs.gaming.rgs.engine.discovery;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class LoadGameEngineEvent extends ApplicationEvent {
    private final boolean register;
    String refId;

    public LoadGameEngineEvent(Object source, boolean register) {
        super(source);
        this.register = register;
        this.refId = UUID.randomUUID().toString();
    }
}