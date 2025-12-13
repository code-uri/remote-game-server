package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Document(collection = "Settings")
@CompoundIndexes({
        @CompoundIndex(name = "deleted_1_tenant_1_uid_1", def = "{deleted: 1, tenant: 1, uid : 1}", unique = true)
})
public class SettingsTemplateDocument extends EntityDocument {

    private String uid;

    private List<String> types;

    private Map<String, StakeSettings> stakes;

    private Map<String, Double> streakWagers;

    private String[] licences;

    private Double[] rtps;

    private StakeSettings defaultStakeSettings;


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SettingsTemplateDocument{");
        sb.append("uid='").append(uid).append('\'');
        sb.append(", stakes=").append(stakes);
        sb.append(", defaultStakeSettings=").append(defaultStakeSettings);
        sb.append(", tags=").append(tags);
        sb.append(", data=").append(data);
        sb.append('}');
        return sb.toString();
    }
}

