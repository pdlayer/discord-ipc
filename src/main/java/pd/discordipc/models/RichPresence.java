package pd.discordipc.models;

import pd.discordipc.utils.TimeUtils;
import java.util.List;

public class RichPresence {
    private String details;
    private String state;
    private ActivityType type = ActivityType.PLAYING;
    private String url;
    private Long startTimestamp;
    private Long endTimestamp;
    private String largeImageKey;
    private String largeImageText;
    private String smallImageKey;
    private String smallImageText;
    private String partyId;
    private Integer partySize;
    private Integer partyMax;
    private String matchSecret;
    private String joinSecret;
    private String spectateSecret;
    private List<Button> buttons;

    public String getDetails() { return details; }
    public String getState() { return state; }
    public ActivityType getType() { return type; }
    public String getUrl() { return url; }
    public Long getStartTimestamp() { return startTimestamp; }
    public Long getEndTimestamp() { return endTimestamp; }
    public String getLargeImageKey() { return largeImageKey; }
    public String getLargeImageText() { return largeImageText; }
    public String getSmallImageKey() { return smallImageKey; }
    public String getSmallImageText() { return smallImageText; }
    public String getPartyId() { return partyId; }
    public Integer getPartySize() { return partySize; }
    public Integer getPartyMax() { return partyMax; }
    public String getMatchSecret() { return matchSecret; }
    public String getJoinSecret() { return joinSecret; }
    public String getSpectateSecret() { return spectateSecret; }
    public List<Button> getButtons() { return buttons; }

    private RichPresence() {}

    public static class Builder {
        private final RichPresence rp = new RichPresence();

        public Builder details(String details) {
            rp.details = details;
            return this;
        }

        public Builder state(String state) {
            rp.state = state;
            return this;
        }

        public Builder type(ActivityType type) {
            rp.type = type;
            return this;
        }

        public Builder streaming(String url) {
            rp.type = ActivityType.STREAMING;
            rp.url = url;
            return this;
        }

        public Builder timer(String time) {
            long now = System.currentTimeMillis() / 1000L;
            rp.startTimestamp = now - TimeUtils.parseToSeconds(time);
            rp.endTimestamp = null;
            return this;
        }

        public Builder loop(String min, String max) {
            long now = System.currentTimeMillis() / 1000L;
            long minSec = TimeUtils.parseToSeconds(min);
            long maxSec = TimeUtils.parseToSeconds(max);
            long diff = maxSec - minSec;
            if (diff > 0) {
                long offset = (now % diff) + minSec;
                rp.startTimestamp = now - offset;
                rp.endTimestamp = null;
            }
            return this;
        }

        public Builder large(String key) {
            rp.largeImageKey = key;
            return this;
        }

        public Builder large(String key, String text) {
            rp.largeImageKey = key;
            rp.largeImageText = text;
            return this;
        }

        public Builder small(String key) {
            rp.smallImageKey = key;
            return this;
        }

        public Builder small(String key, String text) {
            rp.smallImageKey = key;
            rp.smallImageText = text;
            return this;
        }

        public Builder party(String id, int size, int max) {
            rp.partyId = id;
            rp.partySize = size;
            rp.partyMax = max;
            return this;
        }

        public Builder buttons(List<Button> buttons) {
            rp.buttons = buttons;
            return this;
        }

        public Builder button(String label, String url) {
            if (rp.buttons == null) rp.buttons = new java.util.ArrayList<>();
            rp.buttons.add(new Button(label, url));
            return this;
        }

        public RichPresence build() {
            return rp;
        }
    }

    public static class Button {
        private final String label;
        private final String url;

        public Button(String label, String url) {
            this.label = label;
            this.url = url;
        }

        public String getLabel() { return label; }
        public String getUrl() { return url; }
    }
}
