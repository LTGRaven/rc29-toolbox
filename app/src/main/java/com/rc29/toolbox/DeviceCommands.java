package com.rc29.toolbox;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Fixed argument lists only; no shell interpreter, root requests, or downloaded code. */
final class DeviceCommands {
    static final String CATALOG_PROPERTY = "persist.sys.tc.allow.third.install";

    static final class Result {
        final int exitCode;
        final String output;
        Result(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
        boolean ok() { return exitCode == 0; }
    }

    static Result run(String... arguments) {
        Process process = null;
        try {
            process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
            long deadline = SystemClock.elapsedRealtime() + 5000;
            int exit;
            while (true) {
                try { exit = process.exitValue(); break; }
                catch (IllegalThreadStateException running) {
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        process.destroy();
                        return new Result(-1, "The device command timed out.");
                    }
                    Thread.sleep(25);
                }
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[512];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (captured.size() < 4096) captured.write(buffer, 0, Math.min(count, 4096 - captured.size()));
                }
            }
            return new Result(exit, new String(captured.toByteArray(), StandardCharsets.UTF_8).trim());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Result(-1, "The operation was interrupted.");
        } catch (Exception unavailable) {
            return new Result(-1, unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    static Result readCatalog() { return run("/system/bin/getprop", CATALOG_PROPERTY); }
    static Result writeCatalog(boolean allow) {
        return run("/system/bin/setprop", CATALOG_PROPERTY, allow ? "1" : "0");
    }
    static boolean isTrue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value)
                || "y".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }
    static boolean isFalse(String value) {
        return value.isEmpty() || "0".equals(value) || "false".equalsIgnoreCase(value)
                || "n".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value);
    }
    static boolean validPackage(String value) {
        return value != null && value.length() <= 200
                && value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }
}
