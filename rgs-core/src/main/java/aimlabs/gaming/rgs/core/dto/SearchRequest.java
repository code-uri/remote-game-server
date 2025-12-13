package aimlabs.gaming.rgs.core.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

@Data
@NoArgsConstructor
public class SearchRequest implements Serializable {
    private String q;
    private List<String> queryProperties = new ArrayList<>();
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, List<Object>> filters = new HashMap<>();
    private List<String> nonNullProperties = new ArrayList<>();
    private Date from;
    private Date to;
    private String rangeProperty = "createdOn";
    private int size = 25;
    private int page = 0;
    private LinkedList<SortOrder> sort = new LinkedList<>();


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SearchRequest{");
        sb.append("q='").append(q).append('\'');
        sb.append(", queryProperties=").append(queryProperties);
        sb.append(", properties=").append(properties);
        sb.append(", filters=").append(filters);
        sb.append(", nonNullProperties=").append(nonNullProperties);
        sb.append(", from=").append(from);
        sb.append(", to=").append(to);
        sb.append(", rangeProperty='").append(rangeProperty).append('\'');
        sb.append(", size=").append(size);
        sb.append(", page=").append(page);
        sb.append(", sort=").append(sort);
        sb.append('}');
        return sb.toString();
    }
}
