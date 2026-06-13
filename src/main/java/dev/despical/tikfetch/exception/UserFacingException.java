package dev.despical.tikfetch.exception;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public class UserFacingException extends RuntimeException {

    public UserFacingException(String message) {
        super(message);
    }

    public UserFacingException(String message, Throwable cause) {
        super(message, cause);
    }
}
