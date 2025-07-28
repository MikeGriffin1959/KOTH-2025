package services;

import java.util.regex.Pattern;

public class PasswordValidator {
    // Password requirements pattern
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&]?)[A-Za-z\\d@$!%*#?&]{8,}$");
    
    private static final String PASSWORD_REQUIREMENTS = 
        "Password must be at least 8 characters long and contain at least one letter and one number";

    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    public static String getRequirements() {
        return PASSWORD_REQUIREMENTS;
    }
    
    // Get the regex pattern string for JavaScript
    public static String getJavaScriptRegex() {
        return PASSWORD_PATTERN.pattern();
    }
}