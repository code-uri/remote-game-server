package aimlabs.gaming.rgs.core.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class SortOrder implements Serializable {
    private String direction; //Allowed values "ASC", "DESC"
    private String property;
    private boolean ignoreCase = false;


    public SortOrder(String direction, String property) {
        this.direction = direction;
        this.property = property;
    }
}
