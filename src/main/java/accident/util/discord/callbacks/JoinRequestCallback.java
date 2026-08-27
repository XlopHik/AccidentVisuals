package accident.util.discord.callbacks;

import accident.util.discord.DiscordUser;
import com.sun.jna.Callback;

public interface JoinRequestCallback extends Callback {
    void apply(final DiscordUser p0);
}
