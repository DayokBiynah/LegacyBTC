package com.example.legacybtc;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared validated duration input; bare numbers preserve legacy hours. */
final class InheritanceTime {
    private InheritanceTime() { }
    static long seconds(String input, long now) {
        String text=input.trim().toLowerCase(Locale.ROOT);
        long value;
        try {
            if(text.matches("[0-9]+(?:\\s+(?:minutes?|hours?|days?))?")) {
                String[] parts=text.split("\\s+");
                long unit=parts.length==1 || parts[1].startsWith("hour") ? 3600 : parts[1].startsWith("day") ? 86400 : 60;
                value=Math.multiplyExact(Long.parseLong(parts[0]),unit);
            } else {
                value=LocalDateTime.parse(input.trim(),DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT))
                        .atZone(ZoneId.systemDefault()).toEpochSecond()-now;
            }
        } catch(RuntimeException error) { throw new IllegalArgumentException("Use 5 minutes, 2 hours, 3 days, or yyyy-MM-dd HH:mm (local time)."); }
        if(value<60 || value>10L*366*86400) throw new IllegalArgumentException("Choose a future deadline from one minute to ten years.");
        return value;
    }
}
