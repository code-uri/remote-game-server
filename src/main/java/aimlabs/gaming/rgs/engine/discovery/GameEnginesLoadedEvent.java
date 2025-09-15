package aimlabs.gaming.rgs.engine.discovery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

@Getter
public class GameEnginesLoadedEvent extends ApplicationEvent {
    String refId;
    String status;

    public GameEnginesLoadedEvent(Object source, String refId, String status) {
        super(source);
        this.refId = refId;
        this.status = status;
    }
}