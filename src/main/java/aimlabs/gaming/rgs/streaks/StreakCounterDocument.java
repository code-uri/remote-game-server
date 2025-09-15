package aimlabs.gaming.rgs.streaks;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;

@Data
@Document(collection = "StreakCounters")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_game_1_player_1_currency_1", def = "{deleted: 1,tenant: 1, game : 1,  player:1, currency: 1}"),
})
public class StreakCounterDocument extends EntityDocument {

    String player;
    String game;
    int streak;
    BigDecimal streakWin;
    BigDecimal streakWager;
    String currency;
    String lastGameResult;
    Double bonusMultiplier;
    List<Double> streakMultipliers;
    String startGameRoundId;
    String endGameRoundId;
}
