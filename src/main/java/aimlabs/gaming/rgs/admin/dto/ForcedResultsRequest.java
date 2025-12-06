package aimlabs.gaming.rgs.admin.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

@Data
public class ForcedResultsRequest {

    String gameId;
    String session;
    String gamePlay;
    String bonus;
    Integer reel;
    ObjectNode json;
}
