package com.sam.assigment.service;

public enum ViralityInteraction {
    BOT_REPLY(1),
    HUMAN_LIKE(20),
    HUMAN_COMMENT(50);

    private final long points;

    ViralityInteraction(long points) {
        this.points = points;
    }

    public long points() {
        return points;
    }
}
