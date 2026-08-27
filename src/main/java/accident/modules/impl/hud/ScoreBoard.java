package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import accident.client.draggables.AbstractHudElement;
import accident.util.animations.Direction;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class ScoreBoard extends AbstractHudElement {

    private static final float MARGIN        = 5f;
    private static final float ROW_HEIGHT    = 10f;
    private static final float HEADER_HEIGHT = 14f;

    public ScoreBoard() {
        super("ScoreBoard", 10, 200, 120, 20, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.world == null || mc.player == null) {
            stopAnimation();
            return;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = getObjective(scoreboard);

        if (objective != null) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (mc.world == null || mc.player == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = getObjective(scoreboard);
        if (objective == null) return;

        float alphaFactor = alpha / 255.0f;
        float scaleAnim   = scaleAnimation.getOutput().floatValue();

        renderScoreboard(context, scoreboard, objective, alphaFactor, scaleAnim);
    }

    private static final Comparator<ScoreboardEntry> ENTRY_COMPARATOR =
            Comparator.comparingInt(ScoreboardEntry::value).reversed()
                    .thenComparing(e -> e.owner(), String.CASE_INSENSITIVE_ORDER);

    private void renderScoreboard(DrawContext context, Scoreboard scoreboard,
                                  ScoreboardObjective objective,
                                  float alphaFactor, float scaleAnim) {

        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        List<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(ENTRY_COMPARATOR)
                .limit(15)
                .collect(Collectors.toList());

        Text title      = objective.getDisplayName();
        int  titleWidth = mc.textRenderer.getWidth(title);
        int  colonWidth = mc.textRenderer.getWidth(": ");

        int contentWidth = titleWidth;
        for (ScoreboardEntry entry : entries) {
            Team team    = scoreboard.getScoreHolderTeam(entry.owner());
            Text name    = Team.decorateName(team, entry.name());
            Text score   = entry.formatted(numberFormat);
            int  nameW   = mc.textRenderer.getWidth(name);
            int  scoreW  = mc.textRenderer.getWidth(score);
            int  rowW    = nameW + (scoreW > 0 ? colonWidth + scoreW : 0);
            contentWidth = Math.max(contentWidth, rowW);
        }

        float panelWidth  = contentWidth + MARGIN * 2 + 6;
        float panelHeight = HEADER_HEIGHT + entries.size() * ROW_HEIGHT + MARGIN;

        setWidth((int) Math.ceil(panelWidth));
        setHeight((int) Math.ceil(panelHeight));

        float x = getRenderX();
        float y = getRenderY();

        int glassAlpha = (int) (alphaFactor * scaleAnim * 55);
        int bgColor    = (glassAlpha << 24) | 0x0F121F;

        Render2D.blur(x, y, panelWidth, panelHeight, 10f, 4f, bgColor);
        Render2D.outline(x, y, panelWidth, panelHeight, 0.5f,
                new Color(55, 55, 55, (int) (alphaFactor * scaleAnim * 255)).getRGB(), 6);

        int headerBgAlpha = (int) (alphaFactor * scaleAnim * 40);
        Render2D.rect(x, y, panelWidth, HEADER_HEIGHT,
                (headerBgAlpha << 24) | 0x1A1C2E, 6);

        float titleX = x + (panelWidth - titleWidth) / 2f;
        float titleY = y + (HEADER_HEIGHT - 8) / 2f;

        context.drawText(mc.textRenderer, title,
                (int) titleX, (int) titleY,
                new Color(255, 255, 255, (int) (alphaFactor * scaleAnim * 255)).getRGB(),
                false);

        for (int i = 0; i < entries.size(); i++) {
            ScoreboardEntry entry = entries.get(i);
            Team team  = scoreboard.getScoreHolderTeam(entry.owner());
            Text name  = Team.decorateName(team, entry.name());
            Text score = entry.formatted(numberFormat);

            float rowY = y + HEADER_HEIGHT + i * ROW_HEIGHT + 2;

            if (i % 2 == 0) {
                int rowBgAlpha = (int) (alphaFactor * scaleAnim * 12);
                Render2D.rect(x + 1, rowY - 1, panelWidth - 2, ROW_HEIGHT,
                        (rowBgAlpha << 24) | 0xFFFFFF, 0);
            }

            int textAlpha = (int) (alphaFactor * scaleAnim * 230);

            context.drawText(mc.textRenderer, name,
                    (int) (x + MARGIN), (int) rowY,
                    new Color(220, 220, 220, textAlpha).getRGB(),
                    false);

            int scoreW = mc.textRenderer.getWidth(score);
            context.drawText(mc.textRenderer, score,
                    (int) (x + panelWidth - scoreW - MARGIN), (int) rowY,
                    new Color(255, 80, 80, textAlpha).getRGB(),
                    false);
        }
    }

    private ScoreboardObjective getObjective(Scoreboard scoreboard) {
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(mc.player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (slot != null) {
                objective = scoreboard.getObjectiveForSlot(slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        }
        return objective;
    }
}