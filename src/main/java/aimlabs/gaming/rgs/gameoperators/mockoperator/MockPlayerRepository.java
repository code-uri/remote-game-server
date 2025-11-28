package aimlabs.gaming.rgs.gameoperators.mockoperator;

import aimlabs.gaming.rgs.players.Player;
import org.springframework.data.keyvalue.repository.KeyValueRepository;

public interface MockPlayerRepository extends KeyValueRepository<Player, String> {

    Player findByUid(String uid);

    Player findByCorrelationId(String token);
}