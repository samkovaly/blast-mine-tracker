package blastminetracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import lombok.Setter;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

class OrePercentComponent implements LayoutableRenderableEntity
{
    private final BufferedImage image;
    private final String text;

    @Setter
    private Point preferredLocation = new Point();

    private final Rectangle bounds = new Rectangle();

    OrePercentComponent(BufferedImage image, int amount, int total)
    {

        double percent = total > 0 ? (amount * 100.0 / total) : 0.0;
        String percentText = String.format("%.1f%%", percent);

        this.image = image;
        this.text = percentText;
    }

    @Override
    public void setPreferredSize(Dimension dimension)
    {
        // ignore
    }

    @Override
    public Rectangle getBounds()
    {
        return bounds;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (image == null)
        {
            return null;
        }

        final int x = preferredLocation.x;
        final int y = preferredLocation.y;

        graphics.drawImage(image, x, y, null);

        Font oldFont = graphics.getFont();
        graphics.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics metrics = graphics.getFontMetrics();

        int textWidth = metrics.stringWidth(text);
        int textX = x + (image.getWidth() - textWidth) / 2;
        int textY = y + image.getHeight() + metrics.getAscent();

        graphics.setColor(Color.WHITE);
        graphics.drawString(text, textX, textY);

        graphics.setFont(oldFont);

        int width = Math.max(image.getWidth(), textWidth);
        int height = image.getHeight() + metrics.getHeight();

        bounds.setLocation(preferredLocation);
        bounds.setSize(width, height);

        return new Dimension(width, height);
    }
}