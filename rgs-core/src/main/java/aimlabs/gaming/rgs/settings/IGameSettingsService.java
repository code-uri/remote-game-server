package aimlabs.gaming.rgs.settings;

import java.util.Map;

public interface IGameSettingsService {

    Map<String, Object> findGameSettingsForCurrency(String tenant, String brand, String game, String currency);

    Map<String, Object> getBrandGameSettings(String tenant, String brand, String game);
}
