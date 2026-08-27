package accident.events.impl;


import accident.events.api.events.callables.EventCancellable;

public class PostPlayerUpdateEvent extends EventCancellable {
    private int iterations;

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int in) {
        iterations = in;
    }
}