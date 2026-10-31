package com.example.legacybtc;

/** Logs every cause and stack frame without messages that might contain wallet material. */
final class WalletDiagnostics {
    private WalletDiagnostics() { }
    static void failure(String operation, Throwable error) {
        StringBuilder trace = new StringBuilder(operation);
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        append(trace, error, seen, "\n");
        java.util.logging.Logger.getLogger("LegacyBTC.Wallet").severe(trace.toString());
    }
    private static void append(StringBuilder out, Throwable error, java.util.Set<Throwable> seen, String prefix) {
        if (error == null || !seen.add(error)) return;
        out.append(prefix).append(error.getClass().getName());
        for (StackTraceElement frame : error.getStackTrace()) out.append("\n  at ").append(frame);
        for (Throwable suppressed : error.getSuppressed()) append(out, suppressed, seen, "\nSuppressed: ");
        append(out, error.getCause(), seen, "\nCaused by: ");
    }
}

