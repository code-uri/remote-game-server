package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.networks.INetworkService;
import aimlabs.gaming.rgs.networks.Network;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.Player;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Data
@Service
@Slf4j
public class PromotionService extends AbstractEntityService<Promotion, PromotionDocument> implements IPromotionService {

    @Autowired
    PromotionStore store;

    @Autowired
    PromotionMapper mapper;

    @Autowired
    INetworkService networkService;

    @Autowired
    IPlayerService playerService;

    @Autowired
    IGameSkinService gameSkinService;

    @Autowired
    IBrandService brandService;

    public Promotion findByPromotionRefId(String promotionRefId) {

        return store.getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and( "promotionRefId")
                        .is(promotionRefId).and("deleted").is(false)), Promotion.class, "Promotions");

    }

    public Promotion award(FreeSpinsPromotionRequest fspReq) {
        if(!StringUtils.hasLength(fspReq.getPromotionRefId())){
            throw new BaseRuntimeException(SystemErrorCode.INVALID_PROMOTION_REFERENCE, "promotionRefId not found!");
        }

        if(fspReq.getGames()==null || fspReq.getGames().isEmpty() || fspReq.getGames().stream().anyMatch(s -> !StringUtils.hasLength(s)))
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Games list not found.");

        Promotion promotion = findByPromotionRefId(fspReq.getPromotionRefId());
        if(promotion!=null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_PROMOTION_REFERENCE, "Promotion already exists.");

        Network network = networkService.findOneByClientId(fspReq.getClientId());

        if(network==null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_CLIENT_ID, "invalid clientId");


        if ((fspReq.getBrand() == null && fspReq.getPlayer() == null) ) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "player or brand not provided.");
        }


        Player player = null;
        if(fspReq.getPlayer()!=null) {
            player = playerService.findPlayerByNetworkAndCorrelationId(network.getUid(), fspReq.getPlayer());
            if (player == null)
                player = playerService.create(new Player(network.getTenant(), network.getUid(),
                        fspReq.getPlayer(),
                        fspReq.getBrand(),
                        fspReq.getPlayerTags()));
        }
        List<String> games = gameSkinService.findAllByUidIn(fspReq.getGames())
        .stream().map(gameSkin -> {
            if (fspReq.getPayLines() != null && gameSkin.getPayLines() != null
                && fspReq.getPayLines() <= gameSkin.getPayLines()) {
                throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "game " + gameSkin.getUid() + " does not support " + fspReq.getPayLines() + " payLines.");
            }
            return gameSkin.getUid();
        }).toList();

        Brand brand = brandService.findOneByTenantAndBrand(network.getTenant(), fspReq.getBrand());
        if(brand==null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_BRAND);


        if (games.isEmpty()) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "game not found.");
        }
        //Brand brand = gamesAndBrand.getT2();

        promotion = new Promotion();
        promotion.setBetAmounts(fspReq.getBetAmounts());
        promotion.setStartDate(fspReq.getStartDate());
        promotion.setEndDate(fspReq.getEndDate());
        promotion.setFreeSpins(fspReq.getFreeSpins());
        promotion.setPayLines(fspReq.getPayLines());
        promotion.setGames(games);
        promotion.setPlayerTags(fspReq.getPlayerTags());
        promotion.setPromotionRefId(fspReq.getPromotionRefId());
        promotion.setPromotionType(fspReq.getPromotionType());
        promotion.setStatus(Status.ACTIVE);
        promotion.setBrand(fspReq.getBrand());
        promotion.setNetwork(network.getUid());

        if(player!=null) {
            promotion.setPlayer(player.getUid());

        }
        Promotion promotion1 = findByNetworkAndPromotionRefId(promotion.getPlayer(), promotion.getPromotionRefId());

        if(promotion1==null)
            promotion = create(promotion);

        return promotion;
    }

    public Promotion createIfNotFound(Promotion obj) {
        Promotion promotion = findByNetworkAndPromotionRefId(obj.getPlayer(), obj.getPromotionRefId());
        if(promotion==null)
            return create(obj);
        return promotion;
    }

    @Override
    public Promotion closePromotionByPlayer(String promotion, String player) {
        return getStore().getTemplate()
                .findAndModify(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                                .and("id").is(promotion)
                                .and("player").is(player)), Update.update("status", Status.COMPLETED),
                        Promotion.class, "Promotions");
    }

    private Promotion findByNetworkAndPromotionRefId(String network, String promotionRefId) {
        return getStore().getTemplate().findOne(Query.query(Criteria.where("promotionRefId").is(promotionRefId)
                        .and("network").is(network)), Promotion.class, "Promotions");
    }
}

