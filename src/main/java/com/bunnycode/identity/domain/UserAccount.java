package com.bunnycode.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A registered user. Only the columns the application uses are mapped; the rest
 * ({@code follower_count}, {@code experience}) keep their database defaults.
 */
@Entity
@Table(name = "`user`")
public class UserAccount {

    static final String DEFAULT_PICTURE_KEY = "/user/user_icon.png";
    private static final int INITIAL_LEVEL = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "user_name", nullable = false, length = 30)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "profile", length = 100)
    private String profile;

    @Column(name = "picture", nullable = false, length = 100)
    private String pictureKey;

    @Column(name = "level", nullable = false, columnDefinition = "int unsigned")
    private int level;

    protected UserAccount() {
        // for JPA
    }

    private UserAccount(String name, EmailAddress email, String passwordHash) {
        this.name = Objects.requireNonNull(name, "name");
        this.email = email.value();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.pictureKey = DEFAULT_PICTURE_KEY;
        this.level = INITIAL_LEVEL;
    }

    public static UserAccount register(String name, EmailAddress email, String passwordHash) {
        return new UserAccount(name, email, passwordHash);
    }

    /** Re-hash after a successful sign-in when the stored hash uses outdated parameters. */
    public void replacePasswordHash(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getProfile() {
        return profile;
    }

    public String getPictureKey() {
        return pictureKey;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof UserAccount that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        // Constant across the entity's lifecycle (the id is assigned on insert).
        return UserAccount.class.hashCode();
    }

    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", name=" + name + "]";
    }
}
