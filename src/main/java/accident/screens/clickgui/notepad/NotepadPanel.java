package accident.screens.clickgui.notepad;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotepadPanel {

    private static final float SIDEBAR_W  = 132f;
    private static final float HEADER_H   = 28f;
    private static final float ITEM_H     = 20f;
    private static final float ITEM_GAP   = 2f;
    private static final float RADIUS     = 7f;
    private static final float PAD        = 7f;

    public static class Note {
        public String id, title, content;
        public long updatedAt;
        public Note(String title) {
            id = UUID.randomUUID().toString().substring(0, 8);
            this.title = title;
            content    = "";
            updatedAt  = System.currentTimeMillis();
        }
    }

    private final List<Note> notes = new ArrayList<>();
    private String selectedId = null;

    private StringBuilder buf       = new StringBuilder();
    private int           cursorPos = 0;
    private int           selStart  = -1;
    private int           selEnd    = -1;

    private String clipboard = "";

    private static final int    MAX_HISTORY = 100;
    private final List<String>  undoStack   = new ArrayList<>();
    private int                 undoIndex   = -1;
    private long                lastEditMs  = 0;

    private float sidebarScroll       = 0f;
    private float sidebarTargetScroll = 0f;
    private float editorScroll        = 0f;
    private float editorTargetScroll  = 0f;
    private long  lastUpdateMs        = System.currentTimeMillis();

    private String        renamingId   = null;
    private boolean       renameInSide = false;
    private StringBuilder renameBuf    = new StringBuilder();

    private final java.util.Map<String, Float> hoverAnim = new java.util.HashMap<>();

    public NotepadPanel() {
        Note n = new Note("welcome");
        n.content = "Ctrl+N   — new note\nCtrl+W   — delete note\nCtrl+Z/Y — undo / redo\n"
                + "Ctrl+A   — select all\nCtrl+C/X/V — copy / cut / paste\n"
                + "F2       — rename\nHome/End — line start/end\n"
                + "Shift+← → — extend selection";
        notes.add(n);
        openNote(n.id);
    }


    public void render(DrawContext context, int mouseX, int mouseY, float alpha) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastUpdateMs) / 1000f, 0.05f);
        lastUpdateMs = now;

        sidebarScroll += (sidebarTargetScroll - sidebarScroll) * 20f * dt;
        editorScroll  += (editorTargetScroll  - editorScroll)  * 20f * dt;

        int screenW = Render2D.getFixedScaledWidth();
        int screenH = Render2D.getFixedScaledHeight();
        float totalW = Math.min(screenW * 0.82f, 570f);
        float totalH = Math.min(screenH * 0.76f, 390f);
        float ox     = (screenW - totalW) / 2f;
        float oy     = (screenH - totalH) / 2f + 16f;

        {
            float listY = oy + HEADER_H + 3f;
            float curY  = listY + sidebarScroll;
            for (Note n : notes) {
                boolean hov = mouseX >= ox + 4f && mouseX <= ox + SIDEBAR_W - 4f
                        && mouseY >= curY && mouseY <= curY + ITEM_H;
                float h = hoverAnim.getOrDefault(n.id, 0f);
                h += ((hov ? 1f : 0f) - h) * 14f * dt;
                hoverAnim.put(n.id, h);
                curY += ITEM_H + ITEM_GAP;
            }
        }

        int bgA = (int)(alpha * 48);
        Render2D.blur(ox, oy, totalW, totalH, 16f, 4f, getThemeBlurColor(alpha * 0.52f));
        Render2D.rect(ox, oy, totalW, totalH, (bgA << 24) | 0x000000, RADIUS);

        int outA = (int)(alpha * 0.10f * 255);
        Render2D.outline(ox, oy, totalW, totalH, 0.5f, (outA << 24) | 0xFFFFFF, RADIUS);

        renderSidebar(ox, oy, totalH, alpha, mouseX, mouseY, dt);

        float divX = ox + SIDEBAR_W;
        int divA = (int)(alpha * 0.09f * 255);
        Render2D.rect(divX, oy + 8f, 0.5f, totalH - 16f, (divA << 24) | 0xFFFFFF, 0f);

        float edX = divX + 4f;
        float edW = totalW - SIDEBAR_W - 4f;
        renderEditor(context, edX, oy, edW, totalH, alpha);
    }


    private void renderSidebar(float ox, float oy, float totalH,
                               float alpha, int mouseX, int mouseY, float dt) {
        int hA = (int)(alpha * 0.20f * 255);
        Render2D.rect(ox, oy, SIDEBAR_W, HEADER_H, (hA << 24) | 0x000000, RADIUS);

        int titleA = (int)(alpha * 0.55f * 255);
        Fonts.TEST.draw("notes", ox + PAD, oy + HEADER_H / 2f - 3.5f, 6f, (titleA << 24) | 0xB8B8C8);

        float btnX = ox + SIDEBAR_W - 23f;
        float btnY = oy + 6f;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + 16f
                && mouseY >= btnY && mouseY <= btnY + 16f;
        int btnA = (int)(alpha * (btnHov ? 0.38f : 0.16f) * 255);
        Render2D.rect(btnX, btnY, 16f, 16f,
                (btnA << 24) | (btnHov ? getThemeRawColor() : 0x282838), 4f);
        int plusA = (int)(alpha * (btnHov ? 0.95f : 0.55f) * 255);
        Fonts.TEST.draw("+", btnX + 4.5f, btnY + 3.5f, 7f, (plusA << 24) | 0xFFFFFF);

        int cntA = (int)(alpha * 0.30f * 255);
        String cnt = notes.size() + " files";
        Fonts.TEST.draw(cnt, ox + SIDEBAR_W - 23f - Fonts.TEST.getWidth(cnt, 4.5f) - 4f,
                oy + HEADER_H / 2f - 2.5f, 4.5f, (cntA << 24) | 0x777788);

        float listY = oy + HEADER_H + 3f;
        float listH = totalH - HEADER_H - 3f;
        Scissor.enable(ox, listY, SIDEBAR_W, listH, 2);

        float curY = listY + sidebarScroll;
        for (Note note : notes) {
            curY = renderItem(note, ox, curY, SIDEBAR_W, alpha, mouseX, mouseY);
        }

        Scissor.disable();

        float contentH = notes.size() * (ITEM_H + ITEM_GAP);
        if (contentH > listH) {
            float sbX     = ox + SIDEBAR_W - 3f;
            float thumbH  = Math.max(14f, listH * (listH / contentH));
            float maxSc   = contentH - listH;
            float ratio   = maxSc > 0 ? (-sidebarScroll) / maxSc : 0f;
            float thumbY  = listY + (listH - thumbH) * ratio;
            Render2D.rect(sbX, listY, 2f, listH, ((int)(alpha * 0.07f * 255) << 24) | 0xFFFFFF, 1f);
            Render2D.rect(sbX, thumbY, 2f, thumbH, ((int)(alpha * 0.20f * 255) << 24) | 0xFFFFFF, 1f);
        }
    }

    private float renderItem(Note note, float x, float y, float w,
                             float alpha, int mouseX, int mouseY) {
        boolean sel = note.id.equals(selectedId);
        float   ha  = hoverAnim.getOrDefault(note.id, 0f);

        if (sel) {
            int selA = (int)(alpha * 0.28f * 255);
            Render2D.rect(x + 4f, y, w - 8f, ITEM_H,
                    (selA << 24) | getThemeRawColor(), 5f);

            int lineA = (int)(alpha * 0.70f * 255);
            Render2D.rect(x + 4f, y + 3f, 2f, ITEM_H - 6f,
                    (lineA << 24) | getThemeRawColor(), 1f);
        } else if (ha > 0.01f) {
            int hovA = (int)(alpha * ha * 0.12f * 255);
            Render2D.rect(x + 4f, y, w - 8f, ITEM_H, (hovA << 24) | 0xFFFFFF, 5f);
        }

        if (renamingId != null && renamingId.equals(note.id) && renameInSide) {
            int textA = (int)(alpha * 0.92f * 255);
            String rt = renameBuf.toString();
            Fonts.TEST.draw(rt + "▌", x + PAD + 10f, y + ITEM_H / 2f - 3f,
                    5.5f, (textA << 24) | 0xFFFFFF);
        } else {
            int nameA = (int)(alpha * (sel ? 0.88f : 0.55f) * 255);
            int nameC = sel ? 0xF0F0F8 : 0xB0B0C0;
            String display = truncate(note.title, w - PAD * 2 - 14f, 5.5f);
            Fonts.TEST.draw(display, x + PAD + 10f, y + ITEM_H / 2f - 3f,
                    5.5f, (nameA << 24) | nameC);
        }

        return y + ITEM_H + ITEM_GAP;
    }


    private void renderEditor(DrawContext context, float x, float y,
                              float w, float h, float alpha) {
        Note note = getSelected();

        int hA = (int)(alpha * 0.16f * 255);
        Render2D.rect(x, y, w, HEADER_H, (hA << 24) | 0x000000, RADIUS);

        if (note == null) {
            int emA = (int)(alpha * 0.25f * 255);
            String msg = "select or create a note";
            float mw = Fonts.TEST.getWidth(msg, 5.5f);
            Fonts.TEST.draw(msg, x + w / 2f - mw / 2f, y + h / 2f - 3f,
                    5.5f, (emA << 24) | 0x666676);
            return;
        }

        if (renamingId != null && renamingId.equals(note.id) && !renameInSide) {
            int nA = (int)(alpha * 0.90f * 255);
            Fonts.TEST.draw(renameBuf + "▌", x + PAD, y + HEADER_H / 2f - 4f,
                    7f, (nA << 24) | 0xFFFFFF);
        } else {
            int nA = (int)(alpha * 0.72f * 255);
            String display = truncate(note.title, w - 80f, 7f);
            Fonts.TEST.draw(display, x + PAD, y + HEADER_H / 2f - 4f,
                    7f, (nA << 24) | 0xDDDDE8);
        }

        int hintA = (int)(alpha * 0.22f * 255);
        String hint = "F2 rename  Ctrl+W delete";
        Fonts.TEST.draw(hint, x + w - Fonts.TEST.getWidth(hint, 4f) - PAD,
                y + HEADER_H / 2f - 2.5f, 4f, (hintA << 24) | 0x888898);

        float textAreaY = y + HEADER_H + 4f;
        float textAreaH = h - HEADER_H - 4f;
        float textX     = x + PAD + 6f;
        float maxTextW  = w - PAD * 2 - 14f;
        final float LINE_H = 9.0f;

        List<String>  wLines  = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        buildWrapped(buf.toString(), maxTextW, wLines, offsets);

        Scissor.enable(x + 2f, textAreaY, w - 4f, textAreaH, 2);

        float curY = textAreaY + 6f + editorScroll;

        for (int li = 0; li < wLines.size(); li++) {
            String wl    = wLines.get(li);
            int    lStart = offsets.get(li);
            int    lEnd   = lStart + wl.length();
            boolean vis   = curY + LINE_H >= textAreaY && curY <= textAreaY + textAreaH;

            if (vis) {
                if (hasSelection()) {
                    int sA = Math.min(selStart, selEnd);
                    int sB = Math.max(selStart, selEnd);
                    int sa = Math.max(lStart, sA);
                    int sb = Math.min(lEnd, sB);
                    if (sa < sb) {
                        float x1 = textX + Fonts.TEST.getWidth(wl.substring(0, sa - lStart), 6f);
                        float x2 = textX + Fonts.TEST.getWidth(wl.substring(0, sb - lStart), 6f);
                        int selBgA = (int)(alpha * 0.35f * 255);
                        Render2D.rect(x1, curY - 0.5f, Math.max(x2 - x1, 2f), LINE_H,
                                (selBgA << 24) | getThemeRawColor(), 2f);
                    }
                }

                int tA = (int)(alpha * 0.88f * 255);
                if (!wl.isEmpty()) Fonts.TEST.draw(wl, textX, curY, 6f, (tA << 24) | 0xECECF4);

                if (cursorPos >= lStart && cursorPos <= lEnd) {
                    String before = wl.substring(0, cursorPos - lStart);
                    float cx = textX + Fonts.TEST.getWidth(before, 6f);
                    double blink = Math.sin(System.currentTimeMillis() / 240.0) * 0.5 + 0.5;
                    if (blink > 0.25) {
                        int cA = (int)(alpha * blink * 220);
                        Render2D.rect(cx - 0.4f, curY - 0.5f, 0.9f, LINE_H,
                                (cA << 24) | 0xFFFFFF, 0f);
                    }
                }
            }
            curY += LINE_H;
        }

        Scissor.disable();

        float totalTextH = wLines.size() * LINE_H + 14f;
        if (totalTextH > textAreaH) {
            float sbX    = x + w - 3f;
            float ratio  = textAreaH / totalTextH;
            float thumbH = Math.max(12f, textAreaH * ratio);
            float maxSc  = totalTextH - textAreaH;
            float sr     = maxSc > 0 ? (-editorScroll) / maxSc : 0f;
            float thumbY = textAreaY + (textAreaH - thumbH) * sr;
            Render2D.rect(sbX, textAreaY, 2f, textAreaH,
                    ((int)(alpha * 0.07f * 255) << 24) | 0xFFFFFF, 1f);
            Render2D.rect(sbX, thumbY, 2f, thumbH,
                    ((int)(alpha * 0.22f * 255) << 24) | 0xFFFFFF, 1f);
        }

        int statA = (int)(alpha * 0.25f * 255);
        String stat = buf.length() + " ch · " + countLines() + " ln";
        Fonts.TEST.draw(stat, x + w - Fonts.TEST.getWidth(stat, 4f) - PAD,
                y + h - 9f, 4f, (statA << 24) | 0x777788);
    }


    public boolean mouseClicked(double mx, double my, int btn,
                                float panelX, float panelY,
                                float totalW, float totalH) {

        float divX = panelX + SIDEBAR_W + 4f;
        float edW  = totalW - SIDEBAR_W - 4f;

        float btnX = panelX + SIDEBAR_W - 23f, btnY = panelY + 6f;
        if (btn == 0 && mx >= btnX && mx <= btnX + 16f && my >= btnY && my <= btnY + 16f) {
            createNote(); return true;
        }

        if (btn == 0 && getSelected() != null
                && mx >= divX && mx <= divX + edW
                && my >= panelY && my <= panelY + HEADER_H) {
            startRename(getSelected().id, false); return true;
        }

        if (mx >= panelX && mx <= panelX + SIDEBAR_W && my > panelY + HEADER_H) {
            float listY = panelY + HEADER_H + 3f;
            float curY  = listY + sidebarScroll;
            for (Note n : notes) {
                if (my >= curY && my <= curY + ITEM_H) {
                    if (btn == 0) {
                        if (n.id.equals(selectedId)) {
                            startRename(n.id, true);
                        } else {
                            openNote(n.id);
                        }
                    } else if (btn == 1) {
                        startRename(n.id, true);
                    }
                    return true;
                }
                curY += ITEM_H + ITEM_GAP;
            }
        }

        if (btn == 0 && mx >= divX && mx <= divX + edW && my >= panelY + HEADER_H) {
            finishRename();
            clearSelection();
            float textX     = divX + PAD + 6f;
            float maxTextW  = edW - PAD * 2 - 14f;
            float textAreaY = panelY + HEADER_H + 4f + 6f + editorScroll;
            placeCursor((float)mx, (float)my, textX, textAreaY, maxTextW, 9.0f);
            return true;
        }

        return false;
    }


    public boolean keyPressed(int key) {
        long handle = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrl  = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT)    == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)   == GLFW.GLFW_PRESS;

        if (renamingId != null) {
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE -> { finishRename(); return true; }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (renameBuf.length() > 0) renameBuf.deleteCharAt(renameBuf.length() - 1);
                    return true;
                }
            }
            return true;
        }

        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_N -> { createNote(); return true; }
                case GLFW.GLFW_KEY_W -> { deleteSelected(); return true; }
                case GLFW.GLFW_KEY_A -> {
                    selStart = 0; selEnd = buf.length(); cursorPos = buf.length(); return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (hasSelection()) copyToClipboard(getSelectionText());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection()) {
                        copyToClipboard(getSelectionText());
                        pushUndo(); deleteSelection();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String paste = getFromClipboard();
                    if (!paste.isEmpty()) {
                        pushUndo();
                        if (hasSelection()) deleteSelection();
                        buf.insert(cursorPos, paste);
                        cursorPos += paste.length();
                        clearSelection(); sync();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_Z -> { undo(); return true; }
                case GLFW.GLFW_KEY_Y -> { redo(); return true; }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursorPos > 0) {
                        pushUndo();
                        int np = prevWord(cursorPos);
                        buf.delete(np, cursorPos); cursorPos = np;
                        clearSelection(); sync();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> { cursorPos = 0; if (!shift) clearSelection(); return true; }
                case GLFW.GLFW_KEY_END  -> { cursorPos = buf.length(); if (!shift) clearSelection(); return true; }
            }
        }

        if (key == GLFW.GLFW_KEY_F2 && selectedId != null) {
            startRename(selectedId, false); return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) { clearSelection(); return false; }

        if (getSelected() == null) return false;

        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (shift) { if (!hasSelection()) selStart = cursorPos; if (cursorPos > 0) cursorPos--; selEnd = cursorPos; }
                else { if (hasSelection()) { cursorPos = Math.min(selStart, selEnd); clearSelection(); } else if (cursorPos > 0) cursorPos--; }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shift) { if (!hasSelection()) selStart = cursorPos; if (cursorPos < buf.length()) cursorPos++; selEnd = cursorPos; }
                else { if (hasSelection()) { cursorPos = Math.max(selStart, selEnd); clearSelection(); } else if (cursorPos < buf.length()) cursorPos++; }
                return true;
            }
            case GLFW.GLFW_KEY_UP   -> { int np = moveVertical(cursorPos, -1); if (shift) { if (!hasSelection()) selStart = cursorPos; cursorPos = np; selEnd = cursorPos; } else { cursorPos = np; clearSelection(); } return true; }
            case GLFW.GLFW_KEY_DOWN -> { int np = moveVertical(cursorPos, +1); if (shift) { if (!hasSelection()) selStart = cursorPos; cursorPos = np; selEnd = cursorPos; } else { cursorPos = np; clearSelection(); } return true; }
            case GLFW.GLFW_KEY_HOME -> {
                int nl = lineStart(cursorPos);
                if (shift) { if (!hasSelection()) selStart = cursorPos; cursorPos = nl; selEnd = cursorPos; }
                else { cursorPos = nl; clearSelection(); }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                int nl = lineEnd(cursorPos);
                if (shift) { if (!hasSelection()) selStart = cursorPos; cursorPos = nl; selEnd = cursorPos; }
                else { cursorPos = nl; clearSelection(); }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) { pushUndo(); deleteSelection(); }
                else if (cursorPos > 0) { pushUndo(); buf.deleteCharAt(--cursorPos); clearSelection(); sync(); }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) { pushUndo(); deleteSelection(); }
                else if (cursorPos < buf.length()) { pushUndo(); buf.deleteCharAt(cursorPos); sync(); }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                if (hasSelection()) { pushUndo(); deleteSelection(); }
                pushUndo(); buf.insert(cursorPos++, '\n'); clearSelection(); sync();
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (hasSelection()) { pushUndo(); deleteSelection(); }
                pushUndo(); buf.insert(cursorPos, "    "); cursorPos += 4; sync();
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (renamingId != null) {
            if (chr >= 32) { renameBuf.append(chr); return true; }
            return false;
        }
        if (getSelected() == null || chr < 32) return false;
        if (hasSelection()) { pushUndo(); deleteSelection(); }
        pushUndo();
        buf.insert(cursorPos++, chr);
        clearSelection(); sync();
        return true;
    }

    public void mouseScrolled(double mx, double my,
                              float panelX, float panelY,
                              float totalW, float totalH, double vScroll) {
        float divX = panelX + SIDEBAR_W + 4f;
        if (mx < divX) {
            float listH    = totalH - HEADER_H - 3f;
            float contentH = notes.size() * (ITEM_H + ITEM_GAP);
            float minSc    = contentH > listH ? -(contentH - listH) : 0f;
            sidebarTargetScroll = Math.max(minSc, Math.min(0, sidebarTargetScroll + (float)vScroll * 16f));
        } else {
            editorTargetScroll = Math.max(-4000, Math.min(0, editorTargetScroll + (float)vScroll * 16f));
        }
    }


    private void createNote() {
        Note n = new Note("note " + (notes.size() + 1));
        notes.add(0, n);
        openNote(n.id);
        startRename(n.id, true);
    }

    private void openNote(String id) {
        Note n = byId(id); if (n == null) return;
        selectedId = id;
        buf        = new StringBuilder(n.content);
        cursorPos  = buf.length();
        clearSelection();
        editorTargetScroll = 0;
        undoStack.clear(); undoIndex = -1;
        renamingId = null;
    }

    private void deleteSelected() {
        if (selectedId == null) return;
        notes.removeIf(n -> n.id.equals(selectedId));
        selectedId = null; buf = new StringBuilder(); cursorPos = 0; clearSelection();
        if (!notes.isEmpty()) openNote(notes.get(0).id);
    }

    private void startRename(String id, boolean inSide) {
        finishRename();
        renamingId   = id; renameInSide = inSide;
        Note n = byId(id);
        renameBuf    = new StringBuilder(n != null ? n.title : "");
    }

    private void finishRename() {
        if (renamingId == null) return;
        Note n = byId(renamingId);
        if (n != null) { String nm = renameBuf.toString().trim(); n.title = nm.isEmpty() ? "untitled" : nm; }
        renamingId = null;
    }

    private void sync() { Note n = getSelected(); if (n != null) { n.content = buf.toString(); n.updatedAt = System.currentTimeMillis(); } }


    private void pushUndo() {
        long now = System.currentTimeMillis();
        if (undoIndex >= 0 && now - lastEditMs < 350) {
            if (undoIndex < undoStack.size()) undoStack.set(undoIndex, buf.toString());
            lastEditMs = now; return;
        }
        while (undoStack.size() > undoIndex + 1) undoStack.remove(undoStack.size() - 1);
        undoStack.add(buf.toString());
        undoIndex = undoStack.size() - 1;
        if (undoStack.size() > MAX_HISTORY) { undoStack.remove(0); undoIndex--; }
        lastEditMs = now;
    }

    private void undo() {
        if (undoIndex < 0) return;
        if (undoIndex == undoStack.size() - 1) undoStack.add(buf.toString());
        String prev = undoStack.get(undoIndex);
        buf = new StringBuilder(prev); cursorPos = Math.min(cursorPos, buf.length()); clearSelection();
        undoIndex = Math.max(-1, undoIndex - 1); sync();
    }

    private void redo() {
        if (undoIndex + 2 >= undoStack.size()) return;
        undoIndex++;
        buf = new StringBuilder(undoStack.get(undoIndex + 1));
        cursorPos = Math.min(cursorPos, buf.length()); clearSelection(); sync();
    }


    private boolean hasSelection() { return selStart != -1 && selEnd != -1 && selStart != selEnd; }
    private void clearSelection()  { selStart = -1; selEnd = -1; }
    private String getSelectionText() {
        if (!hasSelection()) return "";
        int a = Math.min(selStart, selEnd), b = Math.max(selStart, selEnd);
        return buf.substring(a, b);
    }
    private void deleteSelection() {
        int a = Math.min(selStart, selEnd), b = Math.max(selStart, selEnd);
        buf.delete(a, b); cursorPos = a; clearSelection(); sync();
    }


    private void placeCursor(float mx, float my, float textX, float textStartY, float maxW, float lineH) {
        List<String> wl = new ArrayList<>(); List<Integer> off = new ArrayList<>();
        buildWrapped(buf.toString(), maxW, wl, off);
        float cy = textStartY;
        for (int li = 0; li < wl.size(); li++) {
            if (my >= cy && my < cy + lineH) {
                String line = wl.get(li); int start = off.get(li);
                int best = start; float bestD = Float.MAX_VALUE;
                for (int ci = 0; ci <= line.length(); ci++) {
                    float cx = textX + Fonts.TEST.getWidth(line.substring(0, ci), 6f);
                    float d  = Math.abs(mx - cx);
                    if (d < bestD) { bestD = d; best = start + ci; }
                }
                cursorPos = Math.max(0, Math.min(buf.length(), best)); return;
            }
            cy += lineH;
        }
        cursorPos = buf.length();
    }

    private int moveVertical(int pos, int dir) {
        List<String> wl = new ArrayList<>(); List<Integer> off = new ArrayList<>();
        buildWrapped(buf.toString(), 400f, wl, off);
        int lineIdx = findLine(wl, off, pos);
        int target  = lineIdx + dir;
        if (target < 0) return 0;
        if (target >= wl.size()) return buf.length();
        int srcStart = off.get(lineIdx);
        float xOff   = Fonts.TEST.getWidth(wl.get(lineIdx).substring(0, pos - srcStart), 6f);
        String tLine = wl.get(target); int tStart = off.get(target);
        int best = tStart; float bestD = Float.MAX_VALUE;
        for (int ci = 0; ci <= tLine.length(); ci++) {
            float cx = Fonts.TEST.getWidth(tLine.substring(0, ci), 6f);
            float d  = Math.abs(xOff - cx);
            if (d < bestD) { bestD = d; best = tStart + ci; }
        }
        return best;
    }

    private int findLine(List<String> wl, List<Integer> off, int pos) {
        for (int li = 0; li < wl.size(); li++) {
            int s = off.get(li), e = s + wl.get(li).length();
            if (pos >= s && pos <= e) return li;
        }
        return wl.isEmpty() ? 0 : wl.size() - 1;
    }

    private int lineStart(int pos) { int i = pos; while (i > 0 && buf.charAt(i - 1) != '\n') i--; return i; }
    private int lineEnd(int pos)   { int i = pos; while (i < buf.length() && buf.charAt(i) != '\n') i++; return i; }
    private int prevWord(int pos)  { int i = pos - 1; while (i > 0 && buf.charAt(i - 1) != ' ' && buf.charAt(i - 1) != '\n') i--; return i; }

    private int countLines() {
        int cnt = 1;
        for (int i = 0; i < buf.length(); i++) if (buf.charAt(i) == '\n') cnt++;
        return cnt;
    }


    private void copyToClipboard(String text) {
        clipboard = text;
        try { GLFW.glfwSetClipboardString(
                net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle(), text);
        } catch (Exception ignored) {}
    }

    private String getFromClipboard() {
        try {
            String s = GLFW.glfwGetClipboardString(
                    net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle());
            if (s != null && !s.isEmpty()) return s;
        } catch (Exception ignored) {}
        return clipboard;
    }


    private void buildWrapped(String text, float maxW, List<String> linesOut, List<Integer> offsetsOut) {
        String[] rawLines = text.split("\n", -1);
        int charOff = 0;
        for (String raw : rawLines) {
            if (raw.isEmpty()) {
                linesOut.add(""); offsetsOut.add(charOff);
                charOff++;
                continue;
            }
            StringBuilder line = new StringBuilder();
            int lineStart = charOff;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                line.append(c);
                if (Fonts.TEST.getWidth(line.toString(), 6f) > maxW) {
                    if (line.length() > 1) {
                        String part = line.substring(0, line.length() - 1);
                        linesOut.add(part); offsetsOut.add(lineStart);
                        lineStart += part.length();
                        charOff   += part.length();
                        line = new StringBuilder(String.valueOf(c));
                    } else {
                        linesOut.add(line.toString()); offsetsOut.add(lineStart);
                        lineStart++; charOff++;
                        line = new StringBuilder();
                    }
                }
            }
            if (line.length() > 0 || raw.isEmpty()) {
                linesOut.add(line.toString()); offsetsOut.add(lineStart);
                charOff += line.length();
            }
            charOff++;
        }
    }


    private String truncate(String text, float maxW, float fs) {
        if (Fonts.TEST.getWidth(text, fs) <= maxW) return text;
        while (text.length() > 1 && Fonts.TEST.getWidth(text + "…", fs) > maxW)
            text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private Note getSelected() { return byId(selectedId); }
    private Note byId(String id) {
        if (id == null) return null;
        for (Note n : notes) if (n.id.equals(id)) return n;
        return null;
    }


    private int getThemeBlurColor(float af) {
        ThemesColumn.Theme th = ThemesColumn.getCurrentGlobalTheme();
        if (th == null) th = ThemesColumn.Theme.DEFAULT;
        int[] p = th.palette;
        int base;
        if (p == null || p.length == 0) base = 0x06060E;
        else if (p.length == 1) base = darken(p[0], 0.86f);
        else { long t = System.currentTimeMillis(); float idx = (float)((t%6000L)/6000.0*p.length);
            int i1=(int)idx%p.length, i2=(i1+1)%p.length; base = darken(lerp(p[i1],p[i2],idx-(int)idx),0.86f); }
        int a = clamp((int)(af * 255));
        return (a << 24) | (base & 0xFFFFFF);
    }

    private int getThemeRawColor() {
        ThemesColumn.Theme th = ThemesColumn.getCurrentGlobalTheme();
        if (th == null) th = ThemesColumn.Theme.DEFAULT;
        int[] p = th.palette;
        if (p == null || p.length == 0) return 0x6060AA;
        if (p.length == 1) return p[0] & 0xFFFFFF;
        long t = System.currentTimeMillis(); float idx = (float)((t%6000L)/6000.0*p.length);
        int i1=(int)idx%p.length, i2=(i1+1)%p.length;
        return lerp(p[i1], p[i2], idx-(int)idx) & 0xFFFFFF;
    }

    private static int darken(int c, float f) {
        return (((int)(((c>>16)&0xFF)*(1f-f)))<<16)|(((int)(((c>>8)&0xFF)*(1f-f)))<<8)|(int)((c&0xFF)*(1f-f));
    }
    private static int lerp(int c1, int c2, float f) {
        int r1=(c1>>16)&0xFF,g1=(c1>>8)&0xFF,b1=c1&0xFF;
        int r2=(c2>>16)&0xFF,g2=(c2>>8)&0xFF,b2=c2&0xFF;
        return ((int)(r1+f*(r2-r1))<<16)|((int)(g1+f*(g2-g1))<<8)|(int)(b1+f*(b2-b1));
    }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public float getPanelX(int sw, float tw) { return (sw - tw) / 2f; }
    public float getPanelY(int sh, float th) { return (sh - th) / 2f + 16f; }
    public float getTotalW(int sw) { return Math.min(sw * 0.82f, 570f); }
    public float getTotalH(int sh) { return Math.min(sh * 0.76f, 390f); }
}