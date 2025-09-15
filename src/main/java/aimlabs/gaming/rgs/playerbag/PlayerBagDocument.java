package aimlabs.gaming.rgs.playerbag;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.StringJoiner;

@Data
@Document("PlayerBag")
@CompoundIndexes(value = {
        @CompoundIndex(def = "{'player': 1,'currency': 1,'game': 1, 'session': 1}")
})
public class PlayerBagDocument extends EntityDocument {

    String player;
    String currency;
    String game;
    String session;
    Date expireOn;


    @Override
    public String toString() {
        return new StringJoiner(", ", PlayerBagDocument.class.getSimpleName() + "[", "]")
                .add("player='" + player + "'")
                .add("currency='" + currency + "'")
                .add("game='" + game + "'")
                .add("session='" + session + "'")
                .add("expireOn=" + expireOn)
                .add("data=" + data)
                .toString();
    }
}
