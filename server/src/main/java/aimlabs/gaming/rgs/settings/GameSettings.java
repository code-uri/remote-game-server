package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.util.List;

@Data
public class GameSettings extends BaseDto {

    private String uid;

    private String brand;

    private String game;

    private List<String> settings;
}
