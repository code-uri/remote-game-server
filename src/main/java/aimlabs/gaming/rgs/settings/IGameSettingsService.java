package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.IEntityService;

import java.util.Map;

public interface IGameSettingsService extends IEntityService<GameSettings> {

    Map<String,Object> findGameSettingsForCurrency(String tenant, String brand, String game, String currency);
    Map<String,Object> getBrandGameSettings(String tenant, String brand, String game);
}
