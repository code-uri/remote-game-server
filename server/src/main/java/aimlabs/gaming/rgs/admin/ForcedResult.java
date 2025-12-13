package aimlabs.gaming.rgs.admin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;

@RedisHash("forced-results")
@Data
public class ForcedResult implements Serializable {
    @Id
    Long id;
    @Indexed
    String attributes;
    Integer reel;
    String bonus;
    ObjectNode json;

    public static ForcedResult createInstance(Long id, String gameId, String session, String gamePlay, String bonus,
            Integer reel) {
        ForcedResult forcedResult = new ForcedResult();
        forcedResult.setId(id);
        StringBuilder sb = new StringBuilder(gameId);
        if (session != null)
            sb.append("-").append(session);
        if (gamePlay != null)
            sb.append("-").append(gamePlay);
        if (bonus != null)
            sb.append("-").append(bonus);

        forcedResult.setBonus(bonus);
        forcedResult.setReel(reel);
        forcedResult.setAttributes(sb.toString());
        return forcedResult;
    }
}
