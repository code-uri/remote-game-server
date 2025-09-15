package aimlabs.gaming.rgs.core.exceptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class DefaultErrorCode implements ErrorCode, Serializable {

    String code;
    String description;
    int httpStatusCode;

    @JsonCreator
    public DefaultErrorCode(String code, String description, int httpStatusCode) {
        this.code = code;
        this.description = description;
        this.httpStatusCode = httpStatusCode;
    }

}
