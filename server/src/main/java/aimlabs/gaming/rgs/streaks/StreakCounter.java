package aimlabs.gaming.rgs.streaks;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StreakCounter extends BaseDto {

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

