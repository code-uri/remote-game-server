package aimlabs.gaming.rgs.core.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SearchResponse<T> implements Serializable {
    private List<T> items = new ArrayList<T>();
    private Long count;
}
