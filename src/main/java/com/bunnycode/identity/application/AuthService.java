package com.bunnycode.identity.application;

import com.bunnycode.identity.domain.EmailAddress;
import com.bunnycode.identity.domain.UserAccount;
import com.bunnycode.identity.domain.UserAccountRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.storage.FileStorage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final FileStorage fileStorage;
    /**
     * Compared against when the e-mail is unknown, so a failed sign-in costs the same bcrypt work
     * whether or not the account exists (no user enumeration by timing).
     */
    private final String timingEqualizerHash;

    AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer,
                FileStorage fileStorage) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.fileStorage = fileStorage;
        this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    public AuthResult signUp(String name, String rawEmail, String password) {
        EmailAddress email = EmailAddress.of(rawEmail);
        String userName = name.strip();
        if (users.existsByEmail(email.value())) {
            throw BunnyException.conflict("user.email-taken", "That e-mail address is already registered.");
        }
        if (users.existsByName(userName)) {
            throw BunnyException.conflict("user.name-taken", "That user name is already taken.");
        }
        UserAccount user = UserAccount.register(userName, email, passwordEncoder.encode(password));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent sign-up for the same name or e-mail.
            throw BunnyException.conflict("user.duplicate", "That user name or e-mail address is already registered.");
        }
        return AuthResult.of(tokenIssuer.issueFor(user), UserViews.privateView(user, fileStorage));
    }

    public AuthResult signIn(String rawEmail, String password) {
        UserAccount user = users.findByEmail(rawEmail.strip()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, timingEqualizerHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.replacePasswordHash(passwordEncoder.encode(password)); // flushed by dirty checking
        }
        return AuthResult.of(tokenIssuer.issueFor(user), UserViews.privateView(user, fileStorage));
    }

    private static BunnyException invalidCredentials() {
        return BunnyException.unauthenticated("auth.invalid-credentials", "The e-mail or password is incorrect.");
    }
}
