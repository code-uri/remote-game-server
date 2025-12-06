package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SettingsTemplate extends BaseDto {

    private String uid;

    private List<String> types;

    private Map<String, StakeSettings> stakes;

    private String[] licences;

    private Double[] rtps;

    private StakeSettings defaultStakeSettings;

}
