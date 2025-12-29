package aimlabs.gaming.rgs.engine.gaffetool;

import in.aimlabs.gaming.engine.api.model.ForceGameResult;
import in.aimlabs.gaming.engine.api.service.GameEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class ForcedResultsBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {


    private ApplicationContext applicationContext;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ForceGameResult forceGameResult) {
            log.info("Before init: Found engine forced results bean {}", bean.getClass().getCanonicalName());
            GameEngine gameEngineService = forceGameResult.getGameEngineService();

            ForcedResultsRGSServiceDiscovery forcedResultsRGSServiceDiscovery = applicationContext.getBean(ForcedResultsRGSServiceDiscovery.class);

//            RGSServiceDiscovery rgsServiceDiscovery = applicationContext.getBean(RGSServiceDiscovery.class);


            gameEngineService.supportedGameConfigurations().keySet().forEach(gameConfiguration -> {
                forcedResultsRGSServiceDiscovery.addForceGameResultBean((String) gameConfiguration, forceGameResult);
                //rgsServiceDiscovery.addEngineService((String) gameConfiguration, gameEngineService);
            });
        }
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ForceGameResult) {
            log.info("After init: Found forced results bean {}", bean.getClass().getCanonicalName());
        }
        return bean;
    }

    
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}