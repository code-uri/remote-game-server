package aimlabs.gaming.rgs.gameskins;


import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import java.util.Map;

@Data
@Service
public class GameSkinService extends AbstractEntityService<GameSkin, GameSkinDocument>
        implements IGameSkinService {

    @Autowired
    GameSkinStore store;

    @Autowired
    GameSkinMapper mapper;

    @Autowired
    GameSettingsService gameSettingsService;

    @Cacheable
    public GameSkin findOneByTenantAndGame(String tenant,  String game) {
        return getMapper().asDto(this.getStore().findOneByGame(game));
    }


    @Override
    @Cacheable
    public GameSkin findOneByUid(String key) {
        return super.findOneByUid(key);
    }

    public GameSkin findOneByProviderGame(String providerGame) {
        return getMapper().asDto( getStore().findOneByProviderGame(providerGame));
    }

    @Override
    //@CacheBust
    public GameSkin update(GameSkin obj) {
        return super.update(obj);
    }

    @Override
    //@CacheBust
    public GameSkin updatePartial(String uid, Map<String, Object> values) {
        return super.updatePartial(uid, values);
    }
}
