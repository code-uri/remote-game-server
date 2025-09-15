package aimlabs.gaming.rgs.core.exceptions;

import lombok.Data;

/**
 * This is default run time exception.
 *
 * @author Raghu Koduri
 */
@Data
public class BaseRuntimeException extends RuntimeException implements IError {

    private final ErrorCode errorCode;

    /**
     * Construct a {@code BaseRuntimeException} with the specified detail message.
     *
     * @param errorCode errorCode this should have error code enum value
     */
    public BaseRuntimeException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }

    /**
     * Construct a {@code BaseRuntimeException} with the specified detail message.
     *
     * @param errorCode errorCode this should have error code enum value
     */
    public BaseRuntimeException(ErrorCode errorCode, String message) {
        super(errorCode.getDescription() + ":" + message);
        this.errorCode = errorCode;
    }


    /**
     * Construct a {@code BaseRuntimeException} with the specified detail message
     * and nested exception.
     *
     * @param errorCode errorCode this should have error code enum value
     * @param cause     the nested exception
     */
    public BaseRuntimeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDescription(), cause);
        this.errorCode = errorCode;
    }


    /**
     * Construct a {@code BaseRuntimeException} with the specified detail message
     * and nested exception.
     *
     * @param errorCode errorCode this should have error code enum value
     * @param cause     the nested exception
     */
    public BaseRuntimeException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.getDescription() + ": " + message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Factory method to create base runtime exception.
     *
     * @param errorCode - errorcode with which exception needs to be created.
     * @return - returns ashoka exception.
     */
    public static BaseRuntimeException of(ErrorCode errorCode) {
        return new BaseRuntimeException(errorCode);
    }
}