package com.suryakiran.taskmanagementtool.util;

import java.util.regex.Pattern;

public class PasswordValidator {

    // Updated regex pattern to include period (.)
    private static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&.])[A-Za-z\\d@$!%*?&.]{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    // Private constructor to prevent instantiation
    private PasswordValidator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static boolean validatePassword(String password) {
        return pattern.matcher(password).matches();
    }
}
