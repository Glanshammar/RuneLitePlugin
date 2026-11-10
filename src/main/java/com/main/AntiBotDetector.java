package com.main;

import com.main.utils.GameFunctions;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AntiBotDetector {
    private final Client client;
    private static boolean checkDetected = false;
    private String lastQuestion = "";
    public static boolean isWellActive = false;

    public AntiBotDetector(Client client) {
        this.client = client;
    }

    private static final String[] SKILL_OF_THE_DAY = {
            "Attack",        // day 1
            "Defence",       // day 2
            "Strength",      // day 3
            "Hitpoints",     // day 4
            "Ranged",        // day 5
            "Prayer",        // day 6
            "Magic",         // day 7
            "Woodcutting",   // day 8
            "Firemaking",    // day 9
            "Fletching",     // day 10
            "Fishing",       // day 11
            "Cooking",       // day 12
            "Runecrafting",  // day 13
            "Mining",        // day 14
            "Smithing",      // day 15
            "Crafting",      // day 16
            "Agility",       // day 17
            "Thieving",      // day 18
            "Slayer",        // day 19
            "Farming",       // day 20
            "Herblore",      // day 21
            "Hunter",        // day 22
            "Construction",  // day 23
            "Sailing"        // day 24
    };

    private void solveQuestion(String raw, String plain){
        String lower = plain.toLowerCase();
        List<Token> tokens = tokenize(raw);

        // Server name
        if (lower.contains("name of this server") || lower.contains("server name")) {
            if (lower.contains("backward") || lower.contains("reverse")) {
                GameFunctions.typeText("arola");
                log.info("Anti-Bot - Answer is 'arola'");
            } else {
                GameFunctions.typeText("alora");
                log.info("Anti-Bot - Answer is 'alora'");
            }
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            return;
        }

        // Home city
        if (lower.contains("home city") || (lower.contains("alora") && lower.contains("city"))) {
            GameFunctions.typeText("edgeville");
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is 'arola'");
            return;
        }

        // Combat level
        if (lower.contains("combat level")) {
            Player player = client.getLocalPlayer();
            String combatLevel = String.valueOf(player.getCombatLevel());
            GameFunctions.typeText(combatLevel);
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is combat level {}", combatLevel);
            return;
        }

        // Total level
        if (lower.contains("total level")) {
            int total = 0;
            for (Skill skill : Skill.values()) {
                if (skill == Skill.OVERALL) continue;
                total += client.getRealSkillLevel(skill);
            }
            GameFunctions.typeText(String.valueOf(total));
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is {}", total);
            return;
        }

        // Skill of the day
        if (lower.contains("skill of the day")) {
            int day = LocalDate.now().getDayOfMonth();
            if (day <= 24) {
                GameFunctions.typeText(SKILL_OF_THE_DAY[day - 1].toLowerCase());
                GameFunctions.keyPress(KeyEvent.VK_ENTER);
                log.info("Anti-Bot - Answer is {}", SKILL_OF_THE_DAY[day - 1].toLowerCase());
            } else {
                log.info("ANSWER: random (day {}) — cannot compute from date", day);
            }
            return;
        }

        // Well of goodwill
        if (lower.contains("well of goodwill")) {
            log.info("ANSWER: cannot detect from widget — check RuneLite timer icon");
            return;
        }

        // Backwards spelling: How do you spell 'racecar' backwards?
        if (lower.contains("spell") && (lower.contains("backward") || lower.contains("reverse"))) {
            String quoted = extractQuoted(plain);
            assert quoted != null;
            String answer = String.valueOf(new StringBuilder(quoted).reverse());
            GameFunctions.typeText(answer);
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is {}", answer);
            return;
        }

        // Word in capitals
        if (lower.contains("word in capital") || lower.contains("capital letters")) {
            String word = findCapitalWord(plain);
            GameFunctions.typeText(word);
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is {}", word);
            return;
        }

        // Word that is a specific color
        if (lower.contains("word that is the color") || lower.contains("word in the color")) {
            String word = findColoredWord(tokens);
            GameFunctions.typeText(word);
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is {}", word);
            return;
        }

        // Word next to icon
        if (lower.contains("word next to the icon") || lower.contains("word by the icon")) {
            String word = findWordNextToIcon(tokens);
            GameFunctions.typeText(word);
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is {}", word);
            return;
        }

        // Math
        if (lower.contains("9+10")) {
            GameFunctions.typeText("21");
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is 21");
            return;
        }

        if (lower.contains("9x6")) {
            GameFunctions.typeText("54");
            GameFunctions.keyPress(KeyEvent.VK_ENTER);
            log.info("Anti-Bot - Answer is 54");
            return;
        }

        log.info("ANSWER: ? (unrecognized question type)");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Widget inputWidget = client.getWidget(WidgetInfo.CHATBOX_TITLE);
        if (inputWidget == null) return;

        String questionRaw = inputWidget.getText();
        if (questionRaw == null) return;

        String questionPlain = Text.removeTags(questionRaw);
        if (questionPlain.equals(lastQuestion)) return;

        checkDetected = true;
        lastQuestion = questionPlain;
        log.info("Anti-bot detected!");
        solveQuestion(questionRaw, questionPlain);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getMessage().contains("It will continue granting everyone 30% bonus experience for 2 hours.") ||
                event.getMessage().contains("30% Bonus XP is active from the Well of Goodwill") ||
                event.getMessage().contains("The Well of Goodwill has been filled!")) {
            isWellActive = true;
            log.info("Anti-Bot well timer updated.");
        }

        if (event.getMessage().contains("The Well of Goodwill is no longer granting bonus experience.")) {
            isWellActive = false;
            log.info("Anti-Bot well timer updated.");
        }
    }

    public static boolean isDetected(){ return checkDetected; }

    // ============================================================
    //  Helpers
    // ============================================================
    private String extractQuoted(String plain) {
        Matcher m = Pattern.compile("['\"]([^'\"]+)['\"]").matcher(plain);
        return m.find() ? m.group(1) : null;
    }

    private String findCapitalWord(String plain) {
        for (String w : plain.split("\\s+")) {
            String clean = w.replaceAll("[^A-Za-z]", "");
            if (clean.length() < 2) continue;
            boolean allCaps = true;
            for (char c : clean.toCharArray()) {
                if (!Character.isUpperCase(c)) { allCaps = false; break; }
            }
            if (allCaps) return clean;
        }
        return null;
    }

    private String findColoredWord(List<Token> tokens) {
        for (Token t : tokens) {
            if (!t.isText() || t.colorHex() == null) continue;
            if (t.colorHex().equalsIgnoreCase("000000")) continue; // skip explicit default-black
            for (String w : t.text().trim().split("\\s+")) {
                if (!w.isEmpty()) return w;
            }
        }
        return null;
    }

    private String findWordNextToIcon(List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).isSprite()) continue;
            for (int j = i + 1; j < tokens.size(); j++) {
                if (tokens.get(j).isText()) {
                    for (String w : tokens.get(j).text().trim().split("\\s+")) {
                        if (!w.isEmpty()) return w;
                    }
                }
            }
            for (int j = i - 1; j >= 0; j--) {
                if (tokens.get(j).isText()) {
                    String[] words = tokens.get(j).text().trim().split("\\s+");
                    for (int k = words.length - 1; k >= 0; k--) {
                        if (!words[k].isEmpty()) return words[k];
                    }
                }
            }
        }
        return null;
    }

    // ============================================================
    //  Tokenizer
    // ============================================================
    public static final class Token {
        private final String colorHex;
        private final int spriteId;
        private final String text;

        public Token(String colorHex, int spriteId, String text) {
            this.colorHex = colorHex;
            this.spriteId = spriteId;
            this.text = text;
        }

        public String colorHex() { return colorHex; }
        public String text()     { return text; }

        public boolean isText()   { return text != null; }
        public boolean isSprite() { return spriteId >= 0; }

        @Override
        public String toString() {
            if (isSprite()) return "<img=" + spriteId + ">";
            return "[" + (colorHex != null ? colorHex : "default") + "] " + text;
        }
    }

    private static final Pattern TAG = Pattern.compile(
            "<col=([0-9a-fA-F]{6})>|</col>|<img=(\\d+)>|([^<]+)");

    private static List<Token> tokenize(String raw) {
        List<Token> out = new ArrayList<>();
        if (raw == null) return out;
        Matcher m = TAG.matcher(raw);
        String currentColor = null;
        while (m.find()) {
            if (m.group(1) != null)       currentColor = m.group(1).toLowerCase();
            else if (m.group(2) != null)  out.add(new Token(currentColor, Integer.parseInt(m.group(2)), null));
            else if (m.group(3) != null)  out.add(new Token(currentColor, -1, m.group(3)));
            else                          currentColor = null;
        }
        return out;
    }
}