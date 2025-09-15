package aimlabs.gaming.rgs.streaks;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Data
@Service
public class StreakCounterService extends AbstractEntityService<StreakCounter, StreakCounterDocument> {

    @Autowired
    StreakCounterStore store;

    @Autowired
    StreakCounterMapper mapper;
}
