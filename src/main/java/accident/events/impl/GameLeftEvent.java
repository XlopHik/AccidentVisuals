package accident.events.impl;

import accident.events.api.events.Event;

public class GameLeftEvent implements Event {
    private static final GameLeftEvent INSTANCE = new GameLeftEvent();

    public static GameLeftEvent get() {
        return INSTANCE;
    }
}