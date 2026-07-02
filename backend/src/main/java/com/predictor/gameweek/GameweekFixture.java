package com.predictor.gameweek;

import com.predictor.catalog.Match;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "gameweek_fixtures")
public class GameweekFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gameweek_id")
    private Gameweek gameweek;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    protected GameweekFixture() {
    }

    public GameweekFixture(Gameweek gameweek, Match match) {
        this.gameweek = gameweek;
        this.match = match;
    }

    public Long getId() {
        return id;
    }

    public Gameweek getGameweek() {
        return gameweek;
    }

    public Match getMatch() {
        return match;
    }
}
