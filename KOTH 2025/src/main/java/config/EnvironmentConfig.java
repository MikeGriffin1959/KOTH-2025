package config;

import org.springframework.stereotype.Component;

@Component
public class EnvironmentConfig {
    
    private static final String AWS_REGION_ENV = "AWS_REGION";
    private static final String EB_ENV_NAME = "EB_ENVIRONMENT_NAME";
    
    /**
     * Detects if we're running in AWS environment
     */
    public boolean isAWSEnvironment() {
        return System.getenv(AWS_REGION_ENV) != null;
    }
    
    /**
     * Detects if we're running in test environment
     */
    public boolean isTestEnvironment() {
        String envName = System.getenv(EB_ENV_NAME);
        return envName != null && envName.toLowerCase().contains("test");
    }
    
    /**
     * Determines if cookies should be secure (HTTPS only)
     */
    public boolean useSecureCookies() {
        return isAWSEnvironment();
    }
    
    /**
     * Gets the base URL for the current environment
     */
    public String getBaseUrl() {
        if (isAWSEnvironment()) {
            return isTestEnvironment() ? 
                "https://test.bingmerfest.com" : 
                "https://koth.bingmerfest.com";
        }
        return "http://localhost:8080"; // Local development
    }
    
    /**
     * Gets environment name for logging/debugging
     */
    public String getEnvironmentName() {
        if (!isAWSEnvironment()) {
            return "LOCAL";
        }
        return isTestEnvironment() ? "TEST" : "PRODUCTION";
    }
}