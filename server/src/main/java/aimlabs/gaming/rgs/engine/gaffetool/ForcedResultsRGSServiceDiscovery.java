package aimlabs.gaming.rgs.engine.gaffetool;

import in.aimlabs.gaming.engine.api.model.ForceGameResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ALL")
@Component
public class ForcedResultsRGSServiceDiscovery {

/*    @Autowired
    private ObjectMapper objectMapper;*/

    private final Map<String, ForceGameResult>
            forceGameResultObjectMap = new HashMap<>();

    public void addForceGameResultBean(String gameConfiguration, ForceGameResult forceGameResult) {
        if (forceGameResultObjectMap.containsKey(gameConfiguration))
            throw new IllegalArgumentException("Game configuration " + gameConfiguration + ", already mapped to forcedresult object "
                                               + forceGameResult.getClass().getCanonicalName());
        forceGameResultObjectMap.put(gameConfiguration, forceGameResult);
    }

    public ForceGameResult getForceGameResult(String gameConfiguration) {
        return forceGameResultObjectMap.get(gameConfiguration);
    }

}
