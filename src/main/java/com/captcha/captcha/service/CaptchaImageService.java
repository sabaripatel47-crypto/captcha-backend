package com.captcha.captcha.service;

import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class CaptchaImageService {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final int TARGET_X_MIN = 80;
    private static final int TARGET_X_MAX = 260;
    private static final int TARGET_SIZE = 50;

    private static final String[] PATTERNS = {"star", "flower", "heart", "sun", "balloon", "cat"};
    private static final String[] BG_NAMES = {
            "bg1.png", "bg2.png", "bg3.png", "bg4.png", "bg5.png"
    };

    private final Random random = new Random();

    public static class CaptchaResult {
        public BufferedImage image;
        public int targetX;

        public CaptchaResult(BufferedImage image, int targetX) {
            this.image = image;
            this.targetX = targetX;
        }
    }

    public CaptchaResult generateCaptcha() {
        int targetX = random.nextInt(TARGET_X_MAX - TARGET_X_MIN + 1) + TARGET_X_MIN;
        int targetY = random.nextInt(HEIGHT - TARGET_SIZE * 2 - 20) + TARGET_SIZE + 10;

        BufferedImage background = generateOrLoadBackground();
        BufferedImage combined = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        g.drawImage(background, 0, 0, WIDTH, HEIGHT, null);

        String pattern = PATTERNS[random.nextInt(PATTERNS.length)];
        drawPattern(g, pattern, targetX, targetY, TARGET_SIZE);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 255, 255, 200));
        int barX = targetX + TARGET_SIZE / 2;
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(barX, 10, barX, HEIGHT - 10);

        g.dispose();

        return new CaptchaResult(combined, targetX);
    }

    private BufferedImage generateOrLoadBackground() {
        try {
            File bgDir = ResourceUtils.getFile("classpath:backgrounds/");
            if (bgDir.exists() && bgDir.isDirectory()) {
                File[] files = bgDir.listFiles((d, name) -> name.endsWith(".png") || name.endsWith(".jpg"));
                if (files != null && files.length > 0) {
                    File chosen = files[random.nextInt(files.length)];
                    BufferedImage bg = ImageIO.read(chosen);
                    return scaleTo(bg, WIDTH, HEIGHT);
                }
            }
        } catch (IOException ignored) {
        }

        return generateProceduralBackground();
    }

    private BufferedImage generateProceduralBackground() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        Color c1 = new Color(200 + random.nextInt(56), 150 + random.nextInt(106), 100 + random.nextInt(106));
        Color c2 = new Color(100 + random.nextInt(106), 150 + random.nextInt(106), 200 + random.nextInt(56));
        GradientPaint paint = new GradientPaint(0, 0, c1, WIDTH, HEIGHT, c2);
        g.setPaint(paint);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 0; i < 30; i++) {
            int cx = random.nextInt(WIDTH);
            int cy = random.nextInt(HEIGHT);
            int cr = 10 + random.nextInt(30);
            Color dotColor = new Color(
                    255, 255, 255,
                    30 + random.nextInt(50)
            );
            g.setColor(dotColor);
            g.fill(new Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2));
        }

        for (int i = 0; i < 5; i++) {
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 15 + random.nextInt(25)));
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        g.dispose();
        return img;
    }

    private BufferedImage scaleTo(BufferedImage src, int targetW, int targetH) {
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return scaled;
    }

    private void drawPattern(Graphics2D g, String pattern, int x, int y, int size) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = size / 2;

        Color shadowColor = new Color(0, 0, 0, 60);
        g.setColor(shadowColor);
        g.fill(new Ellipse2D.Double(x + 3, y + 3, size, size));

        switch (pattern) {
            case "star":
                drawStar(g, centerX, centerY, radius);
                break;
            case "flower":
                drawFlower(g, centerX, centerY, radius);
                break;
            case "heart":
                drawHeart(g, centerX, centerY, radius);
                break;
            case "sun":
                drawSun(g, centerX, centerY, radius);
                break;
            case "balloon":
                drawBalloon(g, centerX, centerY, radius);
                break;
            case "cat":
                drawCat(g, centerX, centerY, radius);
                break;
            default:
                g.setColor(Color.WHITE);
                g.fillOval(x, y, size, size);
        }
    }

    private void drawStar(Graphics2D g, int cx, int cy, int r) {
        Color[] colors = {new Color(255, 215, 0), new Color(255, 200, 0), new Color(255, 240, 100)};
        g.setColor(colors[random.nextInt(colors.length)]);
        int points = 5;
        int outer = r;
        int inner = r / 2;
        int[] xs = new int[points * 2];
        int[] ys = new int[points * 2];
        for (int i = 0; i < points * 2; i++) {
            int angle = (int) Math.toRadians(i * 360 / (points * 2) - 90);
            int radius = (i % 2 == 0) ? outer : inner;
            xs[i] = cx + (int) (radius * Math.cos(angle));
            ys[i] = cy + (int) (radius * Math.sin(angle));
        }
        g.fillPolygon(xs, ys, points * 2);
    }

    private void drawFlower(Graphics2D g, int cx, int cy, int r) {
        Color petalColor = new Color(255, 100 + random.nextInt(80), 150 + random.nextInt(80));
        g.setColor(petalColor);
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60);
            int px = cx + (int) (r * 0.6 * Math.cos(angle));
            int py = cy + (int) (r * 0.6 * Math.sin(angle));
            g.fillOval(px - r / 3, py - r / 3, r * 2 / 3, r * 2 / 3);
        }
        g.setColor(Color.YELLOW);
        g.fillOval(cx - r / 4, cy - r / 4, r / 2, r / 2);
    }

    private void drawHeart(Graphics2D g, int cx, int cy, int r) {
        Color heartColor = new Color(255, 80 + random.nextInt(80), 100);
        g.setColor(heartColor);
        int s = r * 2;
        int ox = cx - s / 2;
        int oy = cy - s / 2;
        g.fillOval(ox, oy, s / 2, s / 2);
        g.fillOval(ox + s / 2, oy, s / 2, s / 2);
        int[] xs = {ox, ox + s, ox + s, ox};
        int[] ys = {oy + s / 4, oy + s / 4, oy + s, oy + s};
        g.fillPolygon(new Polygon(new int[]{ox, ox + s / 2, ox + s, ox + s / 2}, new int[]{oy + s / 2, oy + s, oy + s / 2, oy}, 4));
        g.fillPolygon(new int[]{ox, ox + s / 2, ox + s}, new int[]{oy, oy + s, oy}, 3);
    }

    private void drawSun(Graphics2D g, int cx, int cy, int r) {
        Color sunColor = new Color(255, 220 + random.nextInt(35), 50);
        g.setColor(sunColor);
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(255, 200, 50));
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            int x1 = cx + (int) ((r + 5) * Math.cos(angle));
            int y1 = cy + (int) ((r + 5) * Math.sin(angle));
            int x2 = cx + (int) ((r + 15) * Math.cos(angle));
            int y2 = cy + (int) ((r + 15) * Math.sin(angle));
            g.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawBalloon(Graphics2D g, int cx, int cy, int r) {
        Color balloonColor = new Color(200 + random.nextInt(55), 50 + random.nextInt(100), 255);
        g.setColor(balloonColor);
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(cx - r / 2, cy - r, r / 3, r / 3);
        g.setColor(new Color(0, 0, 0, 50));
        g.setStroke(new BasicStroke(2));
        int bottomX = cx;
        int bottomY = cy + r;
        int[] xs = {bottomX - 4, bottomX + 4, bottomX};
        int[] ys = {bottomY, bottomY, bottomY + 15};
        g.drawPolygon(xs, ys, 3);
    }

    private void drawCat(Graphics2D g, int cx, int cy, int r) {
        g.setColor(new Color(80, 80, 80));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        int[] leftEarX = {cx - r, cx - r + r / 3, cx - r + r / 6};
        int[] leftEarY = {cy - r, cy - r, cy - r + r / 2};
        int[] rightEarX = {cx + r, cx + r - r / 3, cx + r - r / 6};
        int[] rightEarY = {cy - r, cy - r, cy - r + r / 2};
        g.fillPolygon(leftEarX, leftEarY, 3);
        g.fillPolygon(rightEarX, rightEarY, 3);
        g.setColor(Color.WHITE);
        g.fillOval(cx - r / 3, cy - r / 3, r / 3, r / 2);
        g.fillOval(cx + r / 10, cy - r / 3, r / 3, r / 2);
        g.setColor(Color.BLACK);
        g.fillOval(cx - r / 4, cy - r / 4, r / 5, r / 4);
        g.fillOval(cx + r / 8, cy - r / 4, r / 5, r / 4);
        g.setColor(Color.PINK);
        int[] noseX = {cx - 3, cx + 3, cx};
        int[] noseY = {cy + r / 5, cy + r / 5, cy + r / 3};
        g.fillPolygon(noseX, noseY, 3);
    }

    public byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public String imageToBase64(BufferedImage image) throws IOException {
        byte[] bytes = imageToBytes(image);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
