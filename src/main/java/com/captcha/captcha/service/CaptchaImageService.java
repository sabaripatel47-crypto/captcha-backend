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

/**
 * 负责验证码图片的生成与编码。
 *
 * 生成流程：
 * 1. 若 resources/backgrounds/ 目录存在且包含 .png/.jpg 文件，随机选取一张背景图并缩放到 320x180
 * 2. 若不存在，则程序化生成一张渐变 + 随机圆点 + 随机线条的背景图
 * 3. 在背景图上随机位置绘制一个图案（星星 / 花 / 心形 / 太阳 / 气球 / 猫）
 * 4. 在图案中心位置画一条垂直辅助线，帮助用户定位目标
 *
 * 对外只暴露两个方法：generateCaptcha() 生成图片，imageToBase64() 转为 base64 字符串。
 */
@Service
public class CaptchaImageService {

    /** 画布宽度（像素），须与前端 canvasWidth 保持一致 */
    private static final int WIDTH = 320;
    /** 画布高度（像素），须与前端 canvasHeight 保持一致 */
    private static final int HEIGHT = 180;
    /** 目标图案 X 坐标随机范围最小值（防止太靠左边缘，图案被裁切） */
    private static final int TARGET_X_MIN = 80;
    /** 目标图案 X 坐标随机范围最大值（防止太靠右边缘） */
    private static final int TARGET_X_MAX = 260;
    /** 目标图案尺寸（正方形区域边长） */
    private static final int TARGET_SIZE = 50;

    /** 可选的目标图案类型，随机选取一种绘制 */
    private static final String[] PATTERNS = {"star", "flower", "heart", "sun", "balloon", "cat"};
    /** 背景图片文件名列表，若 resources/backgrounds/ 目录存在则随机选取 */
    private static final String[] BG_NAMES = {
            "bg1.png", "bg2.png", "bg3.png", "bg4.png", "bg5.png"
    };

    private final Random random = new Random();

    /**
     * 生成验证码的返回值封装类。
     * - image：包含目标图案的完整画布图片（BufferedImage）
     * - targetX：目标图案左上角的 X 坐标，用于前端遮罩定位和后端校验
     */
    public static class CaptchaResult {
        public BufferedImage image;
        public int targetX;

        public CaptchaResult(BufferedImage image, int targetX) {
            this.image = image;
            this.targetX = targetX;
        }
    }

    /**
     * 生成一张完整的验证码图片。
     *
     * 步骤：
     * 1. 随机生成目标图案的 X、Y 坐标
     * 2. 生成或加载背景图
     * 3. 将目标图案绘制到背景图上
     * 4. 在目标图案中心位置画一条垂直辅助线
     *
     * @return CaptchaResult，包含最终图片和目标位置 targetX
     */
    public CaptchaResult generateCaptcha() {
        // 随机目标图案 X 坐标，范围 [80, 260]，避免太靠左/右边缘
        int targetX = random.nextInt(TARGET_X_MAX - TARGET_X_MIN + 1) + TARGET_X_MIN;
        // 随机目标图案 Y 坐标，范围 [TARGET_SIZE+10, HEIGHT-TARGET_SIZE-10]，保证图案完整显示
        int targetY = random.nextInt(HEIGHT - TARGET_SIZE * 2 - 20) + TARGET_SIZE + 10;

        // 步骤 1：生成背景（优先从文件加载，文件不存在则程序化生成）
        BufferedImage background = generateOrLoadBackground();
        // 创建透明通道画布，用于在背景图上叠加图案和辅助线
        BufferedImage combined = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        // 将背景图画到 combined 上（作为最底层）
        g.drawImage(background, 0, 0, WIDTH, HEIGHT, null);

        // 步骤 2：随机选择图案类型并绘制
        String pattern = PATTERNS[random.nextInt(PATTERNS.length)];
        drawPattern(g, pattern, targetX, targetY, TARGET_SIZE);

        // 步骤 3：开启抗锯齿，提升图案边缘质量
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 设置辅助线颜色（白色半透明），帮助用户判断滑块应拖到的目标位置
        g.setColor(new Color(255, 255, 255, 200));
        // 辅助线 X 坐标为图案中心，CAP_ROUND 让线帽圆润
        int barX = targetX + TARGET_SIZE / 2;
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(barX, 10, barX, HEIGHT - 10); // 从顶部画到距底部 10px 处

        g.dispose();

        return new CaptchaResult(combined, targetX);
    }

    /**
     * 优先从 classpath 加载背景图片并缩放到目标尺寸；若文件不存在或加载失败则程序化生成。
     */
    private BufferedImage generateOrLoadBackground() {
        try {
            File bgDir = ResourceUtils.getFile("classpath:backgrounds/");
            if (bgDir.exists() && bgDir.isDirectory()) {
                // 筛选所有 .png 和 .jpg 文件
                File[] files = bgDir.listFiles((d, name) -> name.endsWith(".png") || name.endsWith(".jpg"));
                if (files != null && files.length > 0) {
                    File chosen = files[random.nextInt(files.length)];
                    BufferedImage bg = ImageIO.read(chosen);
                    return scaleTo(bg, WIDTH, HEIGHT);
                }
            }
        } catch (IOException ignored) {
            // 文件不存在或读取失败时，静默回退到程序化生成
        }

        return generateProceduralBackground();
    }

    /**
     * 程序化生成背景图：渐变色填充 + 随机分布的半透明圆点 + 随机线条。
     * 颜色随机但保证前景图案（白色）清晰可见。
     */
    private BufferedImage generateProceduralBackground() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // 随机渐变色：从暖色调到冷色调
        Color c1 = new Color(200 + random.nextInt(56), 150 + random.nextInt(106), 100 + random.nextInt(106));
        Color c2 = new Color(100 + random.nextInt(106), 150 + random.nextInt(106), 200 + random.nextInt(56));
        GradientPaint paint = new GradientPaint(0, 0, c1, WIDTH, HEIGHT, c2);
        g.setPaint(paint);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 绘制 30 个随机位置、大小的半透明白色圆点，模拟纹理背景
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 30; i++) {
            int cx = random.nextInt(WIDTH);
            int cy = random.nextInt(HEIGHT);
            int cr = 10 + random.nextInt(30);
            Color dotColor = new Color(
                    255, 255, 255,
                    30 + random.nextInt(50)  // alpha 30~80，半透明
            );
            g.setColor(dotColor);
            g.fill(new Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2));
        }

        // 绘制 5 条随机位置和方向的半透明白色细线，进一步增加背景复杂度
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

    /**
     * 将任意尺寸的图片缩放到指定尺寸，使用双线性插值保证缩放后图片质量。
     */
    private BufferedImage scaleTo(BufferedImage src, int targetW, int targetH) {
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return scaled;
    }

    /**
     * 在画布指定位置绘制目标图案。
     * 每个图案先用半透明黑色椭圆作为阴影，增强立体感。
     *
     * @param g      Graphics2D 画布上下文
     * @param pattern 图案类型名称（star / flower / heart / sun / balloon / cat）
     * @param x      图案左上角 X 坐标
     * @param y      图案左上角 Y 坐标
     * @param size   图案占据的正方形区域边长
     */
    private void drawPattern(Graphics2D g, String pattern, int x, int y, int size) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 图案中心点坐标（用于大多数图案的对称绘制）
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = size / 2;

        // 绘制阴影，增加图案立体感
        Color shadowColor = new Color(0, 0, 0, 60);
        g.setColor(shadowColor);
        g.fill(new Ellipse2D.Double(x + 3, y + 3, size, size));

        switch (pattern) {
            case "star":    drawStar(g, centerX, centerY, radius); break;
            case "flower":  drawFlower(g, centerX, centerY, radius); break;
            case "heart":   drawHeart(g, centerX, centerY, radius); break;
            case "sun":     drawSun(g, centerX, centerY, radius); break;
            case "balloon": drawBalloon(g, centerX, centerY, radius); break;
            case "cat":     drawCat(g, centerX, centerY, radius); break;
            default:        g.setColor(Color.WHITE); g.fillOval(x, y, size, size);
        }
    }

    // ==================== 各类图案绘制方法 ====================

    /** 绘制五角星，使用内外半径交替顶点 */
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

    /** 绘制六瓣花：6 个花瓣椭圆围绕中心 + 黄色花蕊 */
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

    /** 绘制心形：两个圆 + 上下两个多边形组合 */
    private void drawHeart(Graphics2D g, int cx, int cy, int r) {
        Color heartColor = new Color(255, 80 + random.nextInt(80), 100);
        g.setColor(heartColor);
        int s = r * 2;
        int ox = cx - s / 2;
        int oy = cy - s / 2;
        // 左圆
        g.fillOval(ox, oy, s / 2, s / 2);
        // 右圆
        g.fillOval(ox + s / 2, oy, s / 2, s / 2);
        // 下半部分菱形
        g.fillPolygon(new Polygon(new int[]{ox, ox + s / 2, ox + s, ox + s / 2}, new int[]{oy + s / 4, oy + s, oy + s / 4, oy}, 4));
        // 上半部分三角形
        g.fillPolygon(new int[]{ox, ox + s / 2, ox + s}, new int[]{oy, oy + s, oy}, 3);
    }

    /** 绘制太阳：中心圆 + 8 条放射光线 */
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

    /** 绘制气球：椭圆球体 + 高光椭圆 + 气球结 + 细线 */
    private void drawBalloon(Graphics2D g, int cx, int cy, int r) {
        Color balloonColor = new Color(200 + random.nextInt(55), 50 + random.nextInt(100), 255);
        g.setColor(balloonColor);
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        // 高光（半透明白色小椭圆，位于气球左上）
        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(cx - r / 2, cy - r, r / 3, r / 3);
        // 气球结（小三角形）
        g.setColor(new Color(0, 0, 0, 50));
        g.setStroke(new BasicStroke(2));
        int[] xs = {cx - 4, cx + 4, cx};
        int[] ys = {cy + r, cy + r, cy + r + 15};
        g.drawPolygon(xs, ys, 3);
    }

    /** 绘制卡通猫脸：灰色圆脸 + 两只尖耳朵 + 白色眼睛（黑瞳孔）+ 粉色鼻子 */
    private void drawCat(Graphics2D g, int cx, int cy, int r) {
        // 灰色圆脸
        g.setColor(new Color(80, 80, 80));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        // 左耳（三角形）
        int[] leftEarX = {cx - r, cx - r + r / 3, cx - r + r / 6};
        int[] leftEarY = {cy - r, cy - r, cy - r + r / 2};
        g.fillPolygon(leftEarX, leftEarY, 3);
        // 右耳
        int[] rightEarX = {cx + r, cx + r - r / 3, cx + r - r / 6};
        int[] rightEarY = {cy - r, cy - r, cy - r + r / 2};
        g.fillPolygon(rightEarX, rightEarY, 3);
        // 白色眼睛
        g.setColor(Color.WHITE);
        g.fillOval(cx - r / 3, cy - r / 3, r / 3, r / 2);
        g.fillOval(cx + r / 10, cy - r / 3, r / 3, r / 2);
        // 黑色瞳孔
        g.setColor(Color.BLACK);
        g.fillOval(cx - r / 4, cy - r / 4, r / 5, r / 4);
        g.fillOval(cx + r / 8, cy - r / 4, r / 5, r / 4);
        // 粉色鼻子（三角形）
        g.setColor(Color.PINK);
        int[] noseX = {cx - 3, cx + 3, cx};
        int[] noseY = {cy + r / 5, cy + r / 5, cy + r / 3};
        g.fillPolygon(noseX, noseY, 3);
    }

    /**
     * 将 BufferedImage 编码为 PNG 字节数组。
     */
    public byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    /**
     * 将 BufferedImage 转为 Base64 字符串（可用于 img src）。
     */
    public String imageToBase64(BufferedImage image) throws IOException {
        byte[] bytes = imageToBytes(image);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
