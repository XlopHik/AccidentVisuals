package accident.util.discord;

import java.util.Arrays;
import java.util.List;
import accident.util.discord.callbacks.JoinGameCallback;
import accident.util.discord.callbacks.ErroredCallback;
import accident.util.discord.callbacks.ReadyCallback;
import accident.util.discord.callbacks.SpectateGameCallback;
import accident.util.discord.callbacks.JoinRequestCallback;
import accident.util.discord.callbacks.DisconnectedCallback;
import com.sun.jna.Structure;

public class DiscordEventHandlers extends Structure {
    public DisconnectedCallback disconnected;
    public JoinRequestCallback joinRequest;
    public SpectateGameCallback spectateGame;
    public ReadyCallback ready;
    public ErroredCallback errored;
    public JoinGameCallback joinGame;

    protected List<String> getFieldOrder() {
        return Arrays.asList("ready", "disconnected", "errored", "joinGame", "spectateGame", "joinRequest");
    }


}
