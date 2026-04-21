package com.sam.assigment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

@Entity
@Table(name = "actors")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Actor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private ActorType actorType;

    protected Actor() {
    }

    protected Actor(ActorType actorType) {
        this.actorType = actorType;
    }

    public Long getId() {
        return id;
    }

    public ActorType getActorType() {
        return actorType;
    }
}
