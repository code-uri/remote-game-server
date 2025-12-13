package aimlabs.gaming.rgs.gameplay;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import aimlabs.gaming.rgs.core.entity.Status;
import com.mongodb.BasicDBObject;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "GamePlays")
public class GamePlayDocument extends EntityDocument {

    @Indexed(name = "uid_1", unique = true)
    private String uid;

    private Status gameStatus;

    private String gameType;

    private String gameConfiguration;

    private BasicDBObject gamePlayState;

}
