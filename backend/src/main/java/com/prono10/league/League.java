package com.prono10.league;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "leagues")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 50;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected League() {
    }

    public League(String name, String inviteCode, Long adminUserId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.adminUserId = adminUserId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
