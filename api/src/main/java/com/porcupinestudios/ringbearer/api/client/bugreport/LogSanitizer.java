package com.porcupinestudios.ringbearer.api.client.bugreport;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips private data from log text before a bug report leaves the player's PC. Reports land on a public GitHub repo,
 * so this errs on the side of removing too much. Plain Java (no game classes) so unit tests run it directly.
 */
public final class LogSanitizer {
    private static final List<Pattern> TOKENS = List.of(
            // launcher arguments, plain (--uuid x) or as ModLauncher lists them ([--username, x, ...])
            Pattern.compile("(?i)(--(?:accessToken|uuid|xuid|clientId|username|session)(?:, |[ =]))[^\\s,\\]]+"),
            Pattern.compile("(?i)((?:access_?token|session(?:id)?|refresh_?token|token|auth)\\s*[=:]\\s*)[\"']?[^\\s\"',;]+"),
            // JWT (Microsoft / Minecraft access tokens)
            Pattern.compile("()eyJ[\\w-]{8,}\\.[\\w-]{8,}\\.[\\w-]+"));
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)*\\.[A-Za-z]{2,}\\b"); // not mod@1.2.3
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])");
    // full form needs 4+ groups so log timestamps (12:34:56) survive; compressed form only in brackets ([::1]:25565)
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)(?<![\\w:])(?:[0-9a-f]{1,4}:){3,7}[0-9a-f]{1,4}(?![\\w:])|\\[[0-9a-f:]*::[0-9a-f:]*]");
    private static final Pattern HOME = Pattern.compile("(?i)([A-Z]:[\\\\/]+Users[\\\\/]+|/home/|/Users/)[^\\\\/\\s]+");
    private static final Pattern CONNECTING = Pattern.compile("(Connecting to )\\S+, ?\\d+");
    private static final Pattern CHAT = Pattern.compile("(\\[CHAT\\] ).*");
    private static final Pattern JOINED = Pattern.compile("\\b\\w{3,16}( (?:joined|left) the game)");

    private final List<String> literals = new ArrayList<>();

    /** @param secrets exact strings to remove wherever they appear: player name, UUID, OS user name. */
    public LogSanitizer(String... secrets) {
        for (String s : secrets) {
            if (s != null && s.length() >= 3) {
                literals.add(s);
                if (s.length() == 36 && s.chars().filter(c -> c == '-').count() == 4) {
                    literals.add(s.replace("-", "")); // UUIDs also appear without dashes
                }
            }
        }
    }

    public String clean(String text) {
        if (text == null) {
            return null;
        }
        String s = text;
        for (Pattern p : TOKENS) {
            s = p.matcher(s).replaceAll("$1<token>");
        }
        s = HOME.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + "<user>"));
        s = CONNECTING.matcher(s).replaceAll("$1<server>");
        s = CHAT.matcher(s).replaceAll("$1<chat removed>");
        s = JOINED.matcher(s).replaceAll("<player>$1");
        s = EMAIL.matcher(s).replaceAll("<email>");
        s = IPV4.matcher(s).replaceAll("<ip>");
        s = IPV6.matcher(s).replaceAll("<ip>");
        for (String lit : literals) {
            s = Pattern.compile(Pattern.quote(lit), Pattern.CASE_INSENSITIVE).matcher(s).replaceAll("<player>");
        }
        return s;
    }
}
