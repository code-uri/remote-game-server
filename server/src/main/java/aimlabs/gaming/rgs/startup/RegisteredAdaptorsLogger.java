package aimlabs.gaming.rgs.startup;

import aimlabs.gaming.rgs.gameoperators.PlayerAccountManagerFactory;
import aimlabs.gaming.rgs.gamesupplier.GameSupplierServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RegisteredAdaptorsLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }

        logRegistered("Operator adaptors", PlayerAccountManagerFactory.class);
        logRegistered("Game provider adaptors", GameSupplierServiceFactory.class);
    }

    public RegisteredAdaptorsLogger(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private <T> void logRegistered(String title, Class<T> type) {
        Map<String, T> beans = applicationContext.getBeansOfType(type);

        if (beans.isEmpty()) {
            log.warn("{}: none registered", title);
            return;
        }

        //log.info("{} registered: {}", title, beans.size());

        beans.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    String beanName = entry.getKey();
                    T bean = entry.getValue();

                    log.info("- {} -> {}", beanName, bean.getClass().getName());
                });
    }
}
