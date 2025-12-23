package aimlabs.gaming.rgs.brandgames;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

@Data
public class BrandGame extends BaseDto {

    String brand;

    String game;

    String network;

}
