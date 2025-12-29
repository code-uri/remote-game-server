package aimlabs.gaming.rgs.engine.gaffetool;

import aimlabs.gaming.rgs.games.GameEngineServiceAdaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
@ConditionalOnClass(GameEngineServiceAdaptor.class)
public class ForcedResultsRGSServiceAspectConfiguration {

    @Bean
    public ForcedResultsRGSAspect rgsServiceForcedResult() {
        return new ForcedResultsRGSAspect();
    }
}
