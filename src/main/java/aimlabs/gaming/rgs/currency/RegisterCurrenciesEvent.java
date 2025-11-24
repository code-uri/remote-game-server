package aimlabs.gaming.rgs.currency;

import org.springframework.context.ApplicationEvent;
public class RegisterCurrenciesEvent extends ApplicationEvent {
    public RegisterCurrenciesEvent(Object source) {
        super(source);

    }
}