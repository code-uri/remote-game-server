package aimlabs.gaming.rgs.gamerounds;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;

import java.net.URI;

public interface IGameRoundReplayService {

    URI replayGameRound(GameSession gameSession, GameRound gameRound, GameSkin gameSkin, Brand brand);
}
