package pd.discordipc.utils;

public class TimeUtils {
    public static long parseToSeconds(String time) {
        if (time == null || !time.contains(":")) return 0;
        String[] parts = time.split(":");
        try {
            if (parts.length == 2) {
                return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            } else if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        return 0;
    }
}
