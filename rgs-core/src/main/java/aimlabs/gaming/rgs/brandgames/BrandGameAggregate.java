package aimlabs.gaming.rgs.brandgames;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.gameskins.GameSkin;

public record BrandGameAggregate(Brand brand, GameSkin game, String network, String tenant, Status status) {

}
