package com.tengames.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier safe to expose in URLs and payloads. */
    @Column(name = "public_id", nullable = false, updatable = false)
    private java.util.UUID publicId = java.util.UUID.randomUUID();

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "guide_seen", nullable = false)
    private boolean guideSeen;

    /** Google's stable user id, set for accounts that sign in with Google. */
    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String country;

    @Column(name = "favourite_club_team_id")
    private Long favouriteClubTeamId;

    /** The championship this player follows; independent of their club. */
    @Column(name = "favourite_competition_id")
    private Long favouriteCompetitionId;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName, String country, Long favouriteClubTeamId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.country = country;
        this.favouriteClubTeamId = favouriteClubTeamId;
    }

    public Long getId() {
        return id;
    }

    public java.util.UUID getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Long getFavouriteClubTeamId() {
        return favouriteClubTeamId;
    }

    public void setFavouriteClubTeamId(Long favouriteClubTeamId) {
        this.favouriteClubTeamId = favouriteClubTeamId;
    }

    /** Used by a password reset, once the code proved control of the inbox. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Long getFavouriteCompetitionId() {
        return favouriteCompetitionId;
    }

    public void setFavouriteCompetitionId(Long favouriteCompetitionId) {
        this.favouriteCompetitionId = favouriteCompetitionId;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isGuideSeen() {
        return guideSeen;
    }

    /** Set when the player finishes the welcome guide or skips it. */
    public void markGuideSeen() {
        this.guideSeen = true;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public void linkGoogle(String googleSubject) {
        this.googleSubject = googleSubject;
        this.emailVerified = true;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
