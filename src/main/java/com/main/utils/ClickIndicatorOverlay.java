package com.main.utils;

import com.main.utils.ClickTracker;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;

public class ClickIndicatorOverlay extends Overlay {

    // --- Tunables -----------------------------------------------------
    private static final Color BASE_COLOR   = new Color(255, 210, 0);
    private static final Color RIGHT_COLOR  = new Color(255, 90,  90);

    private static final int  BASE_HALF     = 12;   // box half-size (24x24 base)
    private static final int  PADDING       = 4;    // extra pixels of padding
    private static final long ANIM_MS       = 350;  // fade/expand duration
    private static final long SAFETY_MS     = 1000; // hard cutoff, just in case

    // --- Overlay setup -------------------------------------------------
    @Inject
    public ClickIndicatorOverlay() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS); // clickable area is usually a widget
    }

    @Override
    public Dimension render(Graphics2D g) {
        ClickTracker.Click click = ClickTracker.getLastClick();
        if (click == null) return null;

        long elapsed = System.currentTimeMillis() - click.timestamp();
        if (elapsed > SAFETY_MS) return null;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // progress 0..1, ease-out cubic for a snappy start
        float t = Math.min(1f, elapsed / (float) ANIM_MS);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);

        Color base = (click.button() == java.awt.event.MouseEvent.BUTTON3)
                ? RIGHT_COLOR : BASE_COLOR;

        int cx = click.x();
        int cy = click.y();

        int half = BASE_HALF + PADDING;
        int size = half * 2;
        int x = cx - half;
        int y = cy - half;

        // 1) Soft fill, shrinking from the edges toward the center
        int fillInset = (int) (eased * half * 0.55f);
        int fillAlpha = (int) (110 * (1f - t));
        if (fillAlpha > 0) {
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha));
            g.fillRect(x + fillInset, y + fillInset,
                    size - fillInset * 2, size - fillInset * 2);
        }

        // 2) Main outline
        int outlineAlpha = (int) (255 * (1f - t));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), outlineAlpha));
        g.drawRect(x, y, size, size);

        // 3) Corner brackets (white, fade with outline)
        drawCornerBrackets(g, x, y, size, outlineAlpha);

        // 4) Expanding outer ring
        int expand = (int) (eased * 26);
        int ringAlpha = (int) (190 * (1f - t));
        if (ringAlpha > 0) {
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(255, 230, 120, ringAlpha));
            g.drawRect(x - expand, y - expand,
                    size + expand * 2, size + expand * 2);
        }

        // 5) Small pulse dot at the exact click point
        int dotAlpha = (int) (255 * (1f - t * 0.85f));
        g.setColor(new Color(255, 255, 255, dotAlpha));
        g.fillOval(cx - 2, cy - 2, 4, 4);

        return null;
    }

    private void drawCornerBrackets(Graphics2D g, int x, int y, int size, int alpha) {
        int len = 8;
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(255, 255, 255, alpha));

        // TL
        g.drawLine(x, y, x + len, y);
        g.drawLine(x, y, x, y + len);
        // TR
        g.drawLine(x + size - len, y, x + size, y);
        g.drawLine(x + size, y, x + size, y + len);
        // BL
        g.drawLine(x, y + size, x + len, y + size);
        g.drawLine(x, y + size - len, x, y + size);
        // BR
        g.drawLine(x + size - len, y + size, x + size, y + size);
        g.drawLine(x + size, y + size - len, x + size, y + size);
    }
}
