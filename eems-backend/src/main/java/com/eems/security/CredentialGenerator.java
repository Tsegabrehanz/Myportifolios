package com.eems.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Generates temporary passwords and username/email suggestions for
 * admin-created accounts and admin-triggered password resets. Not a
 * general-purpose password validator - just the generation side.
 */
@Component
public class CredentialGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O - avoids visual ambiguity with 1/0
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz"; // no l
    private static final String DIGITS = "23456789"; // no 0/1
    private static final String SYMBOLS = "!@#$%&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    /**
     * A 12-character temporary password guaranteed to contain at least
     * one uppercase letter, one lowercase letter, one digit, and one
     * symbol, with visually-ambiguous characters (0/O, 1/l/I) excluded
     * since this is meant to be read off a screen and typed by a human.
     */
    public String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        for (int i = sb.length(); i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(ALL.charAt(random.nextInt(ALL.length())));
        }
        // Shuffle so the guaranteed-category characters aren't always in the same positions.
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    /**
     * Suggests an email/username of the form firstname.lastname@eems.local,
     * appending a numeric suffix (.2, .3, ...) until emailTaken returns
     * false. Not a real mailbox - matches this app's existing convention
     * of using @eems.local placeholder addresses for generated accounts.
     */
    public String generateUsername(String firstName, String lastName, Predicate<String> emailTaken) {
        String base = (normalize(firstName) + "." + normalize(lastName));
        String candidate = base + "@eems.local";
        int suffix = 2;
        while (emailTaken.test(candidate)) {
            candidate = base + suffix + "@eems.local";
            suffix++;
        }
        return candidate;
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
