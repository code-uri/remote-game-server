package aimlabs.gaming.rgs.gamesessions;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Data
@Document(collection = "GameSessions")
@CompoundIndexes({
        @CompoundIndex(def = "{ tenant: 1, deleted: 1, createdOn: -1 }", collation = "en", background = true)
})

public class GameSessionDocument extends EntityDocument {

    @Indexed(name = "uid_1", unique = true)
    private String uid;
    @Indexed
    protected String token;
    private String jwt;
    protected Set<String> regulations = new HashSet<>();
    protected boolean demo = false;
    protected Date startedAt;
    protected Date endedAt;
    protected Date lastAccessedAt;
    protected String ipAddress;
    protected String player;
    private List<String> playerTags;
    protected String brand;
    protected String brandUrl;
    protected String network;
    protected String currency;
    private String game;
    private String gameConfiguration;
    private String providerGame;
    private String pamConnector;
    private String gameConnector;
    private boolean aggregateCredits;
    private Map<String,String> urls = new HashMap<>();
    private String jurisdiction;
    private long realityCheckIntervalInSeconds;
    private long elapsedTimeInSeconds;
    private Double minBet;
    private Double maxTotalBet;
    private Double defaultBet;
}
