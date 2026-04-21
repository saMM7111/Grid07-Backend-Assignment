package com.sam.assigment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "bots")
public class Bot extends Actor {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "persona_description", nullable = false, length = 500)
    private String personaDescription;

    protected Bot() {
        super(ActorType.BOT);
    }

    public Bot(String name, String personaDescription) {
        super(ActorType.BOT);
        this.name = name;
        this.personaDescription = personaDescription;
    }

    public String getName() {
        return name;
    }

    public String getPersonaDescription() {
        return personaDescription;
    }
}
