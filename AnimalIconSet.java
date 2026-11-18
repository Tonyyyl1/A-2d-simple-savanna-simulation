import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Procedural vector silhouette icons for inspect-mode rendering.
 */
public class AnimalIconSet
{
    private static final int ICON = 64;

    private final Map<String, BufferedImage> cache = new HashMap<>();

    public BufferedImage iconFor(String species, Color baseColor)
    {
        BufferedImage cached = cache.get(species);
        if(cached != null) {
            return cached;
        }

        BufferedImage image = new BufferedImage(ICON, ICON,
                                                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        Random random = new Random(0xA21A1 + species.hashCode());
        switch(species) {
            case SpeciesRegistry.LION:
                paintLion(g2, baseColor, random);
                break;
            case SpeciesRegistry.CHEETAH:
                paintCheetah(g2, baseColor, random);
                break;
            case SpeciesRegistry.ZEBRA:
                paintZebra(g2, baseColor, random);
                break;
            case SpeciesRegistry.BUFFALO:
                paintBuffalo(g2, baseColor, random);
                break;
            case SpeciesRegistry.GAZELLE:
                paintGazelle(g2, baseColor, random);
                break;
            default:
                paintGenericQuadruped(g2, baseColor, random);
                break;
        }
        g2.dispose();

        cache.put(species, image);
        return image;
    }

    private void paintLion(Graphics2D g2, Color base, Random random)
    {
        Ellipse2D.Double body = quadrupedBody(g2, base, 30, 17, 8, 13, 9, 14, 0.12);
        double headCX = body.x + body.width + 8 * 0.5;
        double headCY = body.y + body.height * 0.12;
        Color mane = shade(base, 0.55);
        int bumps = 12;
        for(int i = 0; i < bumps; i++) {
            double angle = 2 * Math.PI * i / bumps + random.nextDouble() * 0.05;
            double bx = headCX + Math.cos(angle) * 8 * 1.35;
            double by = headCY + Math.sin(angle) * 8 * 1.35;
            double r = 3.2 + random.nextDouble() * 1.2;
            g2.setColor(mane);
            g2.fill(new Ellipse2D.Double(bx - r, by - r, r * 2, r * 2));
        }
    }

    private void paintCheetah(Graphics2D g2, Color base, Random random)
    {
        Ellipse2D.Double body = quadrupedBody(g2, base, 32, 13, 6, 15, 6, 18, 0.10);
        Color spot = shade(base, 0.5);
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(body);
        for(int i = 0; i < 14; i++) {
            double x = body.x + random.nextDouble() * body.width;
            double y = body.y + random.nextDouble() * body.height;
            double r = 1.3 + random.nextDouble() * 1.2;
            g2.setColor(spot);
            g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        }
        g2.setClip(oldClip);
    }

    private void paintZebra(Graphics2D g2, Color base, Random random)
    {
        Ellipse2D.Double body = quadrupedBody(g2, base, 30, 16, 7, 15, 8, 12, 0.12);
        Color stripe = shade(base, 0.45);
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(body);
        g2.setStroke(new BasicStroke(2f));
        int stripes = 7;
        for(int i = 0; i < stripes; i++) {
            double x = body.x + body.width * (i + 0.5) / stripes;
            g2.setColor(stripe);
            g2.draw(new Line2D.Double(x, body.y - 2, x, body.y + body.height + 2));
        }
        g2.setClip(oldClip);
    }

    private void paintBuffalo(Graphics2D g2, Color base, Random random)
    {
        Ellipse2D.Double body = quadrupedBody(g2, base, 32, 18, 7, 12, 10, 10, 0.45);
        double headCX = body.x + body.width + 7 * 0.5;
        double headCY = body.y + body.height * 0.45;
        Color horn = shade(base, 0.35);
        g2.setColor(horn);
        g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND,
                                     BasicStroke.JOIN_ROUND));
        g2.draw(new QuadCurve2D.Double(
            headCX - 2, headCY - 7,
            headCX - 9, headCY - 10,
            headCX - 6, headCY - 2));
        g2.draw(new QuadCurve2D.Double(
            headCX + 4, headCY - 7,
            headCX + 10, headCY - 9,
            headCX + 7, headCY - 1));
    }

    private void paintGazelle(Graphics2D g2, Color base, Random random)
    {
        Ellipse2D.Double body = quadrupedBody(g2, base, 24, 12, 5, 17, 5, 10, 0.08);
        double headCX = body.x + body.width + 5 * 0.5;
        double headCY = body.y + body.height * 0.08;
        Color horn = shade(base, 0.4);
        g2.setColor(horn);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
                                     BasicStroke.JOIN_ROUND));
        g2.draw(new CubicCurve2D.Double(
            headCX, headCY - 5,
            headCX - 1, headCY - 11,
            headCX - 5, headCY - 13,
            headCX - 4, headCY - 16));
        g2.draw(new CubicCurve2D.Double(
            headCX + 2, headCY - 5,
            headCX + 3, headCY - 11,
            headCX + 7, headCY - 13,
            headCX + 6, headCY - 16));
    }

    private void paintGenericQuadruped(Graphics2D g2, Color base, Random random)
    {
        quadrupedBody(g2, base, 28, 15, 6, 14, 7, 12, 0.15);
    }

    private Ellipse2D.Double quadrupedBody(Graphics2D g2, Color base,
        double bodyW, double bodyH, double headR, double legLen,
        double neckThickness, double tailLen, double headDropFraction)
    {
        double groundY = 52;
        double bodyLeft = 8;
        double bodyBottom = groundY - legLen;
        double bodyTop = bodyBottom - bodyH;
        Ellipse2D.Double body = new Ellipse2D.Double(bodyLeft, bodyTop,
                                                     bodyW, bodyH);
        double bodyRight = bodyLeft + bodyW;
        double bodyCenterY = bodyTop + bodyH / 2.0;

        Color outline = shade(base, 0.4);
        Color legColor = shade(base, 0.85);
        double legW = Math.max(2.0, bodyW * 0.09);
        double[] legX = {
            bodyLeft + bodyW * 0.18, bodyLeft + bodyW * 0.34,
            bodyLeft + bodyW * 0.66, bodyLeft + bodyW * 0.82
        };
        g2.setStroke(new BasicStroke(1f));
        for(double lx : legX) {
            Rectangle2D.Double leg = new Rectangle2D.Double(
                lx - legW / 2, bodyBottom - 2, legW, legLen + 2);
            g2.setColor(legColor);
            g2.fill(leg);
            g2.setColor(outline);
            g2.draw(leg);
        }

        double tailStartX = bodyLeft + 2;
        double tailEndX = tailStartX - tailLen * 0.8;
        double tailEndY = bodyCenterY - tailLen * 0.5;
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(outline);
        g2.draw(new Line2D.Double(tailStartX, bodyCenterY, tailEndX, tailEndY));
        g2.fill(new Ellipse2D.Double(tailEndX - 2, tailEndY - 2, 4, 4));

        double headCX = bodyRight + headR * 0.5;
        double headCY = bodyTop + bodyH * headDropFraction;
        g2.setStroke(new BasicStroke((float)neckThickness,
                                     BasicStroke.CAP_ROUND,
                                     BasicStroke.JOIN_ROUND));
        g2.setColor(base);
        g2.draw(new Line2D.Double(
            bodyRight - bodyW * 0.08, bodyTop + bodyH * 0.25,
            headCX, headCY));

        g2.setColor(base);
        g2.fill(body);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(outline);
        g2.draw(body);

        Ellipse2D.Double head = new Ellipse2D.Double(
            headCX - headR, headCY - headR, headR * 2, headR * 2);
        g2.setColor(base);
        g2.fill(head);
        g2.setColor(outline);
        g2.draw(head);

        return body;
    }

    private static Color shade(Color color, double factor)
    {
        int r = clamp((int)Math.round(color.getRed() * factor));
        int g = clamp((int)Math.round(color.getGreen() * factor));
        int b = clamp((int)Math.round(color.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
