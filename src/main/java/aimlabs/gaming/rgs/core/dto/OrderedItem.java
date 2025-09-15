package aimlabs.gaming.rgs.core.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderedItem implements Serializable {
    private String id;
    private String key;
    private int displayOrder;
}