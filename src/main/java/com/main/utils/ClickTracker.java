package com.main.utils;

public final class ClickTracker {

    public static final class Click {
        private final int x;
        private final int y;
        private final int button;
        private final long timestamp;

        public Click(int x, int y, int button, long timestamp) {
            this.x = x;
            this.y = y;
            this.button = button;
            this.timestamp = timestamp;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int button() { return button; }
        public long timestamp() { return timestamp; }
    }

    private static volatile Click lastClick = null;

    private ClickTracker() {}

    public static void record(int x, int y, int button) {
        lastClick = new Click(x, y, button, System.currentTimeMillis());
    }

    public static Click getLastClick() {
        return lastClick;
    }

    public static void clear() {
        lastClick = null;
    }
}