package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "GameSettings")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_game_1_brand_1", def = "{deleted: 1,tenant: 1, game: 1, brand : 1}", sparse = true, unique = true)
})
public class GameSettingsDocument extends EntityDocument {

    private String brand;

    private String game;

    private List<String> settings;

    public GameSettingsDocument(String brand, String game) {
        this.brand = brand;
        this.game = game;
    }
}
