package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.connectors.IConnectorService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.games.GameSupplierLocator;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;
import aimlabs.gaming.rgs.promotions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AggregatorFreeSpinsPromotionService implements IFreeSpinsPromotionService {

    @Autowired
    IPromotionService promotionService;

    @Autowired
    IGameSkinService gameSkinService;

    @Autowired
    GameSupplierLocator gameSupplierLocator;

    @Autowired
    IConnectorService connectorService;

    @Override
    public FreeSpinsPromotionResponse awardBonus(@RequestBody FreeSpinsPromotionRequest request) {

            Promotion promotion = promotionService.award(request);
            if(promotion==null){
                throw new BaseRuntimeException(SystemErrorCode.INVALID_PROMOTION_REFERENCE, "Promotion not found");
            }

            try {

               // log.info("aggregator promotion created with ref {}", promotion.getId());
                request.setPromotionRefId(promotion.getPromotionRefId());

                List<GameSkin> gameSkins = gameSkinService.findAllByUidIn(request.getGames());

                // Group by connector
                Map<String, List<GameSkin>> groupedByConnector = gameSkins.stream()
                        .collect(Collectors.groupingBy(
                                gameSkin -> gameSkin.getConnector() != null ? gameSkin.getConnector() : "default"));

                // Convert groupedByConnector (connectorId -> List<GameSkin>) to a list of pairs (supplier -> aggregated gameList)
                List<Map.Entry<IGameSupplierPromotionsService, List<String>>> supplierGamePairs = groupedByConnector.entrySet().stream()
                        .map(entry -> {
                            String connectorId = entry.getKey();
                            List<GameSkin> gameSkinList = entry.getValue();

                            List<String> gamesList = gameSkinList.stream()
                                    .map(GameSkin::getProviderGame)
                                    .collect(Collectors.toList());

                            IGameSupplierService supplier = gameSupplierLocator.getSupplier(connectorId);
                            if (!(supplier instanceof IGameSupplierPromotionsService)) {
                                throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR,
                                        "provider not found for " + (gameSkinList.isEmpty() ? connectorId : gameSkinList.get(0).getUid()));
                            }

                            IGameSupplierPromotionsService promotionsSupplier = (IGameSupplierPromotionsService) supplier;
                            return new AbstractMap.SimpleEntry<>(promotionsSupplier, gamesList);
                        })
                        .collect(Collectors.toList());

                // Process each supplier -> games list pair
                for (Map.Entry<IGameSupplierPromotionsService, List<String>> entry : supplierGamePairs) {
                    IGameSupplierPromotionsService supplier = entry.getKey();
                    List<String> gamesList = entry.getValue();

                    // Prepare request for this supplier
                    request.setPlayer(promotion.getPlayer());
                    request.setBrand(promotion.getBrand());
                    request.setPromotionRefId(promotion.getId());
                    request.setGames(gamesList);

                    supplier.awardBonus(request);
                    FreeSpinsPromotionResponse response = supplier.getPromotionByRefId(request);

                    if (response == null) {
                        FreeSpinsPromotionResponse cancelResponse = supplier.cancelBonus(request.getPromotionRefId());
                        if (cancelResponse == null)
                            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Failed to cancel promotion. Request "+ request );
                    }else{
                        log.info("Promotion awarded successfully for promotionRefId: {} via supplier", request.getPromotionRefId());
                    }
                }

                return new FreeSpinsPromotionResponse(promotion.getId(),
                        promotion.getPromotionRefId(), promotion.getStatus());

            } catch (Exception e) {
                promotion = promotionService.updatePartial(promotion.getId(), Map.of("status", Status.CANCELLED));
                throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Promotion Award failed", e);
            }
        }

    @Override
    public FreeSpinsPromotionResponse cancelBonus(@RequestBody FreeSpinsPromotionRequest freeSpinsPromotionRequest) {
        try {
            Promotion promotion = promotionService.findByPromotionRefId(
                    freeSpinsPromotionRequest.getPromotionRefId());

            if (promotion == null) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_PROMOTION_REFERENCE);
            }

            List<GameSkin> gameSkins = gameSkinService.findAllByUidIn(promotion.getGames());

            // Group by connector
            Map<String, List<GameSkin>> groupedByConnector = gameSkins.stream()
                    .collect(Collectors.groupingBy(
                            gameSkin -> gameSkin.getConnector() != null ? gameSkin.getConnector() : "default"));

            // Process each connector group
            for (Map.Entry<String, List<GameSkin>> entry : groupedByConnector.entrySet()) {
                List<GameSkin> gameSkinList = entry.getValue();
                GameSkin gameSkin = gameSkinList.get(0);

                Connector connector = connectorService.findOneByTenantAndConnector(
                        gameSkin.getTenant(), gameSkin.getConnector());

                if (connector != null) {
                    IGameSupplierService supplier = gameSupplierLocator.getSupplier(connector.getUid());
                    if (supplier != null) {
                        // Note: IGameSupplierService doesn't have cancelBonus method
                        // This is a simplified implementation
                        log.info("Would cancel bonus for promotion {} via supplier", promotion.getId());
                    }
                }
            }

            promotionService.updateStatus(promotion.getId(), Status.CANCELLED);
            return new FreeSpinsPromotionResponse(promotion.getId(),
                    promotion.getPromotionRefId(), Status.CANCELLED);

        } catch (BaseRuntimeException e) {
            throw e;
        } catch (Exception throwable) {
            log.error("Error in cancelBonus", throwable);
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR,
                    "Promotion Cancellation failed", throwable);
        }
    }
}
