package aimlabs.gaming.rgs.core.exceptions;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This is default exception
 *
 * @author Raghu Koduri
 *
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BaseException extends Exception implements IError{

    private final ErrorCode errorCode;

    /**
     * Construct a {@code BaseException} with the specified error code.
     *
     * @param errorCode errorCode this should have error code enum value
     */
    public BaseException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }

    /**
     * Construct a {@code BaseException} with the specified error code.
     *
     * @param errorCode errorCode this should have error code enum value
     */
    public BaseException(ErrorCode errorCode, String message) {
        super(errorCode.getDescription() + ":" + message);
        this.errorCode = errorCode;
    }

    /**
     * Construct a {@code BaseException} with the specified error code
     * and nested exception.
     *
     * @param errorCode errorCode this should have error code enum value
     * @param cause     the nested exception
     */
    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDescription(), cause);
        this.errorCode = errorCode;
    }


    /**
     * Construct a {@code BaseException} with the specified error code
     * and nested exception.
     *
     * @param errorCode errorCode this should have error code enum value
     * @param cause     the nested exception
     */
    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.getDescription() + ":" + message, cause);
        this.errorCode = errorCode;
    }


    /**
     * Factory method to create base exception.
     *
     * @param errorCode - errorcode with which exception needs to be created.
     * @return - returns ashoka exception.
     */
    public static BaseException of(ErrorCode errorCode) {
        return new BaseException(errorCode);
    }
}