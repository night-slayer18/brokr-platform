package io.brokr.security.utils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates password strength according to security requirements.
 * 
 * Password Requirements:
 * - Minimum 8 characters, maximum 128 characters
 * - At least one lowercase letter (a-z)
 * - At least one uppercase letter (A-Z)
 * - At least one digit (0-9)
 * - At least one special character from the allowed set
 * - No whitespace characters (space, tab, newline, or any Unicode whitespace)
 * 
 * Allowed Special Characters:
 * ! @ # $ % ^ & * ( ) _ + = - [ ] { } | \ : ; " ' < > , . ? / ~ `
 */
@Component
public class PasswordValidator {

    private static final Pattern LOWER_CASE = Pattern.compile(".*[a-z].*");
    private static final Pattern UPPER_CASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    // Allowed special characters: !@#$%^&*()_+-=[]{}|\:;"'<>,.?/~`
    private static final Pattern SPECIAL_CHAR = Pattern.compile(".*[!@#$%^&*()_+=\\-\\[\\]{}|\\\\:;\"'<>,.?/~`]");

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    public boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }

        boolean hasLowerCase = LOWER_CASE.matcher(password).find();
        boolean hasUpperCase = UPPER_CASE.matcher(password).find();
        boolean hasDigit = DIGIT.matcher(password).find();
        boolean hasSpecialChar = SPECIAL_CHAR.matcher(password).find();
        // Check for any Unicode whitespace character
        boolean hasWhitespace = password.chars().anyMatch(Character::isWhitespace);

        return hasLowerCase && hasUpperCase && hasDigit && hasSpecialChar && !hasWhitespace;
    }

    public List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null) {
            errors.add("Password cannot be null");
            return errors;
        }

        if (password.length() < MIN_LENGTH) {
            errors.add("Password must be at least " + MIN_LENGTH + " characters long");
        }

        if (password.length() > MAX_LENGTH) {
            errors.add("Password must be no more than " + MAX_LENGTH + " characters long");
        }

        if (!LOWER_CASE.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter");
        }

        if (!UPPER_CASE.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter");
        }

        if (!DIGIT.matcher(password).find()) {
            errors.add("Password must contain at least one digit");
        }

        if (!SPECIAL_CHAR.matcher(password).find()) {
            errors.add("Password must contain at least one special character (!@#$%^&*()_+-=[]{}|\\:;\"'<>,.?/~`)");
        }

        // Check for any Unicode whitespace character (space, tab, newline, non-breaking space, etc.)
        boolean hasWhitespace = password.chars().anyMatch(Character::isWhitespace);
        if (hasWhitespace) {
            errors.add("Password cannot contain whitespace characters");
        }

        return errors;
    }
}