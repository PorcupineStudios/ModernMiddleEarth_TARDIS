package com.porcupinestudios.ringbearer.api.client.bugreport;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings of the in-game bug reporter. relayUrl defaults to the pack's deployed relay. */
public final class BugReportConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> RELAY_URL = BUILDER
            .comment("Address of the bug report relay (tools/bug-relay in the repo).",
                    "Empty turns sending off: the report screen says so.")
            .define("bugReport.relayUrl", "https://ringbearer-bugs.aadagosta.workers.dev");
    public static final ModConfigSpec.IntValue LOG_LINES = BUILDER
            .comment("How many of the last lines of logs/latest.log a report carries.")
            .defineInRange("bugReport.logLines", 600, 50, 5000);
    public static final ModConfigSpec.IntValue CRASH_REPORT_MAX_AGE_HOURS = BUILDER
            .comment("The newest crash report goes with a report if it is at most this many hours old (0 = never).")
            .defineInRange("bugReport.crashReportMaxAgeHours", 48, 0, 24 * 365);
    public static final ModConfigSpec.IntValue SCREENSHOT_MAX_WIDTH = BUILDER
            .comment("Screenshots wider than this are scaled down before sending, to keep reports small.")
            .defineInRange("bugReport.screenshotMaxWidth", 1600, 320, 7680);

    public static final ModConfigSpec.ConfigValue<String> ADMIN_KEY = BUILDER
            .comment("The pack maker's admin key: reports sent with it skip the relay's daily limit. It must match the",
                    "relay's ADMIN_KEY secret (tools/bug-relay/README.md). Leave empty; never share a config holding it.")
            .define("bugReport.adminKey", "");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BugReportConfig() {}
}
