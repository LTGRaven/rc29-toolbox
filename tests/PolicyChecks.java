package com.rc29.toolbox;

/** Pure JVM checks for untrusted package input and Android property value parsing. */
public final class PolicyChecks {
    public static void main(String[] args) {
        String[] valid = {"com.block.juggle", "com.rc29.toolbox", "org.example.app_2"};
        String[] invalid = {null, "", "Block Blast!", "com.example;id", "com.example && reboot",
                "com.example\nreboot", "$(id)", "com.example/app", "--user", "com..example",
                "https://play.google.com/store/apps/details?id=com.block.juggle", "com.example'", "com.1app"};
        for (String value : valid) check(DeviceCommands.validPackage(value), "Valid package rejected: " + value);
        for (String value : invalid) check(!DeviceCommands.validPackage(value), "Unsafe package accepted: " + value);
        for (String value : new String[]{"1", "true", "YES", "on", "y"}) check(DeviceCommands.isTrue(value), "True property rejected");
        for (String value : new String[]{"", "0", "false", "NO", "off", "n"}) check(DeviceCommands.isFalse(value), "False property rejected");
        check(!DeviceCommands.isTrue("permission denied"), "Error must not mean enabled");
        check(!DeviceCommands.isFalse("permission denied"), "Error must not mean disabled");
        System.out.println("Package input and property parsing checks passed.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
