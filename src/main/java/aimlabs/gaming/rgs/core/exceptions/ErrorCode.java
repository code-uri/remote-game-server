package aimlabs.gaming.rgs.core.exceptions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.Serializable;

/**
 * This interface is marker interface for ErrorCodes enum .
 *
 * @author Suresh Reddy
 * @since 1.0.0
 */
@JsonDeserialize(as = DefaultErrorCode.class)
public interface ErrorCode extends Serializable {

     String getCode();
     String getDescription();
     int getHttpStatusCode();
}