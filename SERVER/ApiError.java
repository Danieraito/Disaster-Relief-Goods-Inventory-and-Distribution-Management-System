package SERVER;

/** An error that is sent to the browser with a status code and a readable message. */
final class ApiError extends RuntimeException {
    final int status;

    ApiError(int status, String message) {
        super(message);
        this.status = status;
    }
}
