package accident.util.repository.way;

import net.minecraft.util.math.BlockPos;

public record Way(String name, BlockPos pos, String server) {

    public static float animatedAngle = 0f;
    public static float animatedRadius = 0f;
}