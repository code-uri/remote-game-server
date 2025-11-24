package aimlabs.gaming.rgs.core.event;


import org.springframework.context.ApplicationEvent;

public class EntityUpdateEvent extends ApplicationEvent {
    public EntityUpdateEvent(Object source) {
        super(source);
    }

    public String toString() {
        return "Update entity event with source"+getSource();
    }

}
