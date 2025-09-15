package aimlabs.gaming.rgs.brandgames;


import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "BrandGames")
@CompoundIndexes({
        @CompoundIndex(name = "disabled_1_tenant_1_network_1_brand_1_game_1", def = "{deleted: 1,tenant: 1, network: 1, brand : 1, game: 1}", unique = true)
})
public class BrandGameDocument extends EntityDocument {

    private String brand;

    private String game;

    private String network;
}
