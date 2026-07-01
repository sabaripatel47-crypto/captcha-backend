package com.captcha.captcha.service;

import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Base64;
import java.util.List;

/**
 * 负责验证码图片的生成与编码
 *
 * 生成流程：
 * 1. 从 static/img/bg/ 目录加载背景图片并缩放到 320x180
 * 2. 从 static/img/icon/ 目录加载图标图片并绘制到背景上
 * 3. 在图标中心位置画一条垂直辅助线，帮助用户定位目标
 *
 * 对外只暴露两个方法：generateCaptcha() 生成图片，imageToBase64() 转为 base64 字符串。
 */
@Service
public class CaptchaImageService {

    /** 画布宽度（像素），须与前端 canvasWidth 保持一致 */
    private static final int WIDTH = 320;
    /** 画布高度（像素），须与前端 canvasHeight 保持一致 */
    private static final int HEIGHT = 180;
    /** X 坐标随机范围最小值（防止太靠左边缘） */
    private static final int X_MIN = 80;
    /** X 坐标随机范围最大值（防止太靠右边缘，留出 50px 给车体本身） */
    private static final int X_MAX = 230;
    /** 目标图案尺寸（正方形区域边长） */
    private static final int TARGET_SIZE = 50;

    /**
     * icon 文件名列表，
     * 每次生成时从中随机选一个作为"目标 icon"，其余作为混淆 icon 各画一个
     */
    private static final String[] ICON_POOL = {
            "icon-1-车.png", "icon-2-猫.png", "icon-3-猪.png", "icon-4-老虎.png", "icon-5-马.png"
    };
    /** 背景图片文件名列表，从 static/img/bg/ 目录加载 */
    private static final String[] BG_NAMES = {
            "bg-1.png", "bg-2.png"
    };

    private final Random random = new Random();

    /**
     * 生成验证码的返回值封装类
     * - image：包含目标图案的完整画布图片（BufferedImage）
     * - targetX：目标图案左上角的 X 坐标，用于前端遮罩定位和后端校验
     * - tip：提示文本（已拼接好，如"拖动滑块直到出现车"），前端直接展示
     */
    public static class CaptchaResult {
        public BufferedImage image;
        public int targetX;
        public String tip;

        public CaptchaResult(BufferedImage image, int targetX, String tip) {
            this.image = image;
            this.targetX = targetX;
            this.tip = tip;
        }
    }

    /**
     * 生成一张完整的验证码图片
     *
     * 步骤：
     * 1. 从全部 icon 池里随机选一个作为目标 icon
     * 2. 在画布上随机生成 2~3 个目标 icon 位置（错开 y），targetX 取最右那个的 x
     * 3. 加载背景图，把目标 icon 全部画上
     * 4. 把剩余每种 icon 各画一个作为混淆（最多 4 个），位置随机且不与目标 icon 重叠
     * 5. tip 文案按选中的目标 icon 动态拼接
     *
     * @return CaptchaResult，包含最终图片、目标位置 targetX（最右目标 icon 的 x）和提示文本
     */
    public CaptchaResult generateCaptcha() {
        // 步骤 1：从 icon 池中随机选一个作为本次的目标 icon
        String targetIconName = ICON_POOL[random.nextInt(ICON_POOL.length)];
        // 从文件名提取中文标签：例如 "icon-1-车.png" -> "车"
        String targetLabel = targetIconName.replaceAll("^icon-\\d+-", "").replaceAll("\\.png$", "");

        // 步骤 2：随机生成 2~3 个目标 icon 位置(nextInt(2):[0,1])
        int targetCount = 2 + random.nextInt(2); // [2, 3]
        //目标位置数组
        List<int[]> targetPositions = new ArrayList<>();
        int targetAttempts = 0;//重试计数器(overlapsAny会拒绝掉很多随机位置,超过200次上限后,会退出循环)
        // 没有放够目标icon数量,继续循环
        while (targetPositions.size() < targetCount && targetAttempts++ < 200) {
            int tx = random.nextInt(X_MAX - X_MIN + 1) + X_MIN;//取最小值和最大值之间的一个随机位置
            //TARGET_SIZE+10为Y坐标最小值(10px为安全边距),上下流出TARGET_SIZE的安全距离
            int ty = random.nextInt(HEIGHT - TARGET_SIZE * 2) + TARGET_SIZE + 10;
            // 互相之间避免重叠
            if (!overlapsAny(tx, ty, targetPositions)) {
                targetPositions.add(new int[]{tx, ty});
            }
        }

        // 步骤 3：加载背景图
        BufferedImage background = loadBackground();
        BufferedImage combined = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.drawImage(background, 0, 0, WIDTH, HEIGHT, null);//开始绘制

        // 步骤 4：绘制所有目标 icon
        BufferedImage targetImage = loadIcon(targetIconName);
        if (targetImage != null) {
            //根据目标位置逐步绘画每一个目标图标
            for (int[] pos : targetPositions) {
                g.drawImage(targetImage, pos[0], pos[1], TARGET_SIZE, TARGET_SIZE, null);
            }
        }

        // 步骤 5：把 icon 池里除目标外的每个 icon 各画一个作为混淆
        for (String name : ICON_POOL) {
            //与目标图标名称相同,跳过
            if (name.equals(targetIconName)) continue;
            //加载混淆图标
            BufferedImage dIcon = loadIcon(name);
            if (dIcon == null) continue;

            // 随机位置,最多重试 20 次避免与目标 icon 重叠
            int attempts = 0;
            while (attempts++ < 20) {
                int dx = random.nextInt(WIDTH - TARGET_SIZE);
                int dy = random.nextInt(HEIGHT - TARGET_SIZE);
                // 如果没有重叠,添加这个图标
                if (!overlapsAny(dx, dy, targetPositions)) {
                    g.drawImage(dIcon, dx, dy, TARGET_SIZE, TARGET_SIZE, null);
                    break;
                }
            }
        }
        // targetX目标坐标取最右边icon的坐标
        int targetX = 0;
        for (int[] pos : targetPositions) {
            if (pos[0] > targetX) targetX = pos[0];
        }
        //去除误差
        targetX+=22;
        //拼接前端需要提示的文本
        String tip = "拖动滑块直到出现所有" + targetLabel;
        return new CaptchaResult(combined, targetX, tip);
    }

    /**
     * 判断给定坐标 (x, y) 处放置 TARGET_SIZE 的方块,是否与 givenPositions 中任一矩形重叠
     * （留 4px 边距避免视觉粘连）
     */
    private boolean overlapsAny(int x, int y, List<int[]> givenPositions) {
        int SIZE = TARGET_SIZE;
        for (int[] pos : givenPositions) {
            // 四个条件都成立才算有折叠(POS[0]:矩形的左上角x坐标,POS[1]:矩形的左上角y坐标),4px作为安全边距
            boolean overlap = x < pos[0] + SIZE - 4 && x + SIZE - 4 > pos[0]
                    && y < pos[1] + SIZE - 4 && y + SIZE - 4 > pos[1];
            if (overlap) return true;
        }
        return false;
    }

    /**
     * 从 static/img/bg/ 目录加载背景图片并缩放到目标尺寸
     */
    private BufferedImage loadBackground() {
        try {
            File bgDir = ResourceUtils.getFile("classpath:static/img/bg/");
            if (bgDir.exists() && bgDir.isDirectory()) {
                File[] files = bgDir.listFiles((d, name) -> name.endsWith(".png") || name.endsWith(".jpg"));
                if (files != null && files.length > 0) {
                    File chosen = files[random.nextInt(files.length)];
                    BufferedImage bg = ImageIO.read(chosen);//读取到对应文件
                    return scaleTo(bg, WIDTH, HEIGHT);//缩放到指定尺寸
                }
            }
        } catch (IOException ignored) {
        }
        // 加载失败时生成纯色背景
        return generateFallbackBackground();
    }

    /**
     * 从 static/img/icon/ 目录加载指定图标图片
     */
    private BufferedImage loadIcon(String iconName) {
        try {
            File iconFile = ResourceUtils.getFile("classpath:static/img/icon/" + iconName);
            if (iconFile.exists()) {
                BufferedImage icon = ImageIO.read(iconFile);//读取文件
                return scaleTo(icon, TARGET_SIZE, TARGET_SIZE);//缩放到指定尺寸
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * 加载失败时的兜底纯色背景
     */
    private BufferedImage generateFallbackBackground() {
        //创建一个指定尺寸的图片 
        // TYPE_INT_RGB:每个像素用 3 个字节（R、G、B 各 1 字节，0~255） 存储颜色，不带 alpha 透明度通道
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(180, 180, 180));//设置颜色为灰色
        g.fillRect(0, 0, WIDTH, HEIGHT);//填充矩形
        g.dispose();
        return img;
    }

    /**
     * 将任意尺寸的图片缩放到指定尺寸，使用双线性插值保证缩放后图片质量
     */
    private BufferedImage scaleTo(BufferedImage src, int targetW, int targetH) {
        //判断图片是否带透明通道
        int imageType = src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(targetW, targetH, imageType);
        Graphics2D g = scaled.createGraphics();
        //使用双线性插值保证缩放后图片质量
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);//绘制图片
        g.dispose();
        return scaled;
    }

    /**
     * 将 BufferedImage 编码为 PNG 字节数组
     */
    public byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    /**
     * 将 BufferedImage 转为 Base64 字符串（可用于 img src）
     */
    public String imageToBase64(BufferedImage image) throws IOException {
        byte[] bytes = imageToBytes(image);
        return Base64.getEncoder().encodeToString(bytes);//编码为base64字符串
    }
}
