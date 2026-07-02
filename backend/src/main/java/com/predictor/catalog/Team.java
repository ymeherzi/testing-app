package com.predictor.catalog;

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

    public String getProviderRef() {
        return providerRef;
    }
}
