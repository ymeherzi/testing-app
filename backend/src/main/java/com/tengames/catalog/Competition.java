package com.tengames.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "competitions")
public class Competition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "provider_ref", unique = true)
    private String providerRef;

    /**
     * True for a domestic league. False for the Champions League and the cups,
     * whose squad lists are full of clubs that belong to a league elsewhere —
     * stamping from those would make Real Madrid a Champions League club — and
     * which nobody would name as their favourite championship.
     */
    @Column(nullable = false)
    private boolean domestic = true;

    protected Competition() {
    }

    /** Manually curated competition (no data provider behind it). */
    public Competition(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isDomestic() {
        return domestic;
    }

    public void setDomestic(boolean domestic) {
        this.domestic = domestic;
    }

    public String getProviderRef() {
        return providerRef;
    }
}
