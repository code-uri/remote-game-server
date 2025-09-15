package aimlabs.gaming.rgs.gameskins;

import aimlabs.gaming.rgs.core.IEntityService;

import java.util.Map;

public interface IGameSkinService extends IEntityService<GameSkin> {
    GameSkin findOneByUid(String key);
    GameSkin findOneByProviderGame(String providerGame);
    GameSkin update(GameSkin obj);
    GameSkin updatePartial(String uid, Map<String, Object> values);
}