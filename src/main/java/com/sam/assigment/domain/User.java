package com.sam.assigment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends Actor {

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    protected User() {
        super(ActorType.USER);
    }

    public User(String username, boolean premium) {
        super(ActorType.USER);
        this.username = username;
        this.premium = premium;
    }

    public String getUsername() {
        return username;
    }

    public boolean isPremium() {
        return premium;
    }
}
