package com.tengames.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "crest_url")
    private String crestUrl;

    @Column(name = "provider_ref", unique = true)
    private String providerRef;

    /** Three-letter abbreviation, when the provider gives one: PSG, FCB, MUN. */
    private String tla;

    /**
     * The league this club plays in, or null. Null is ordinary: a club that
     * reached us through a cup tie has no squad list to be stamped from.
     */
    @Column(name = "primary_competition_id")
    private Long primaryCompetitionId;

    protected Team() {
    }

    public Team(String name, String shortName, String crestUrl, String providerRef) {
        this.name = name;
        this.shortName = shortName;
        this.crestUrl = crestUrl;
        this.providerRef = providerRef;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getCrestUrl() {
        return crestUrl;
    }

    public void setCrestUrl(String crestUrl) {
        this.crestUrl = crestUrl;
    }

    public String getTla() {
        return tla;
    }

    public void setTla(String tla) {
        this.tla = tla;
    }

    public Long getPrimaryCompetitionId() {
        return primaryCompetitionId;
    }

    /** Set from a domestic league's squad list, and only from there. */
    public void setPrimaryCompetitionId(Long primaryCompetitionId) {
        this.primaryCompetitionId = primaryCompetitionId;
    }

    public String getProviderRef() {
        return providerRef;
    }
}
