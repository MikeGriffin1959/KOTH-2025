package helpers;

public enum LoginResult {
    SUCCESS,
    INVALID_USERNAME,
    INVALID_PASSWORD,
    ERROR;

    LoginResult() {
        // This empty constructor is actually unnecessary, 
        // but adding it explicitly might resolve IDE warnings
    }
}