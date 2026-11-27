package com.porcupinestudios.ringbearer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.porcupinestudios.ringbearer.api.client.bugreport.LogSanitizer;
import org.junit.jupiter.api.Test;

class LogSanitizerTest {
    private final LogSanitizer s = new LogSanitizer("Steve_42", "0b1c2d3e-1111-2222-3333-444455556666", "aadag");

    @Test
    void removesPrivateData() {
        String log = """
                [27Nov2026 12:34:56.789] [main/INFO] Setting user: Steve_42
                ModLauncher running: args [--username, NotPassedIn, --clientId, abc123xyz, --version, neoforge]
                --username Steve_42 --uuid 0b1c2d3e111122223333444455556666 --accessToken eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9.abc
                Loading C:\\Users\\aadag\\curseforge\\minecraft\\Instances\\x and /home/bob/.minecraft
                Connecting to play.example.net, 25565
                remote /203.0.113.7:25565 and [2001:db8::1]:25565 and fe80:0:0:0:200:f8ff:fe21:67cf
                [CHAT] <Alex> my secret message
                Alex joined the game
                mail me at someone@example.com, session=abcdef123
                """;
        String out = s.clean(log);
        for (String leak : new String[] {"Steve_42", "0b1c2d3e", "eyJ", "aadag", "bob", "play.example.net",
                "203.0.113.7", "2001:db8", "NotPassedIn", "abc123xyz", "fe80", "secret", "Alex", "someone@", "abcdef123"}) {
            assertFalse(out.contains(leak), () -> "leaked " + leak + " in:\n" + out);
        }
        // useful parts survive: timestamps, paths below the user folder, the log level
        assertTrue(out.contains("[27Nov2026 12:34:56.789] [main/INFO]"), out);
        assertTrue(out.contains("C:\\Users\\<user>\\curseforge"), out);
    }

    @Test
    void leavesOrdinaryLinesAlone() {
        String line = "[12:00:01] [Render thread/WARN] Missing texture tardis_rb:block/console (21.1.259) at iris@1.8.14-beta.1";
        assertEquals(line, s.clean(line));
    }
}
