package aimlabs.gaming.rgs.settings;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SettingsChangeEvent extends ApplicationEvent {
    String key;
    public SettingsChangeEvent(Object source, String key) {
        super(source);

        this.key = key;
    }

    public String toString() {
        return "SettingsChangeEvent";
    }
}