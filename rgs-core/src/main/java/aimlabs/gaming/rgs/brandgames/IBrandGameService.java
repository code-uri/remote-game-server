package aimlabs.gaming.rgs.brandgames;

import aimlabs.gaming.rgs.core.IEntityService;

import java.util.List;

public interface IBrandGameService extends IEntityService<BrandGame> {

    List<BrandGame> findAllByNetwork(String network);

    BrandGameAggregate findOneByNetworkAndBrandAndGameId(String network, String brand, String gameId);
}
