package com.captcha.captcha.service;

import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

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
    /** 目标图案 X 坐标随机范围最小值（防止太靠左边缘） */
    private static final int TARGET_X_MIN = 80;
    /** 目标图案 X 坐标随机范围最大值（防止太靠右边缘） */
    private static final int TARGET_X_MAX = 260;
    /** 目标图案尺寸（正方形区域边长） */
    private static final int TARGET_SIZE = 50;

    /** 图标文件名列表，从 static/img/icon/ 目录加载 */
    private static final String[] ICON_NAMES = {
            "icon-1-车.png", "icon-2-猫.png"
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
     * 1. 随机生成目标图标的 X、Y 坐标
     * 2. 从本地加载背景图
     * 3. 从本地加载图标图片并绘制到背景图上
     *
     * @return CaptchaResult，包含最终图片和目标位置 targetX
     */
    public CaptchaResult generateCaptcha() {
        // 随机目标图标 X 坐标，范围 [80, 260]，避免太靠左/右边缘
        int targetX = random.nextInt(TARGET_X_MAX - TARGET_X_MIN + 1) + TARGET_X_MIN;
        // 随机目标图标 Y 坐标，范围 [TARGET_SIZE+10, HEIGHT-TARGET_SIZE-10]，保证图标完整显示
        int targetY = random.nextInt(HEIGHT - TARGET_SIZE * 2 - 20) + TARGET_SIZE + 10;

        // 步骤 1：加载背景图
        BufferedImage background = loadBackground();
        // 创建透明通道画布，用于在背景图上叠加图标和辅助线
        BufferedImage combined = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        // 将背景图画到 combined 上（作为最底层）
        g.drawImage(background, 0, 0, WIDTH, HEIGHT, null);

        // 步骤 2：加载本地图标图片并绘制，同时从文件名提取中文生成提示文本
        // 例如 "icon-1-车.png" -> 提取出 "车"，再拼成 "拖动滑块直到出现车"
        int iconIndex = random.nextInt(ICON_NAMES.length);
        String iconName = ICON_NAMES[iconIndex];
        BufferedImage iconImage = loadIcon(iconName);
        if (iconImage != null) {
            g.drawImage(iconImage, targetX, targetY, TARGET_SIZE, TARGET_SIZE, null);
        }

        // 步骤 3：开启抗锯齿，提升图标边缘质量
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 设置辅助线颜色（白色半透明），帮助用户判断滑块应拖到的目标位置
        g.setColor(new Color(255, 255, 255, 200));

        g.dispose();

        // 从文件名中提取中文作为图标名（去掉 "icon-x-" 前缀和 ".png" 后缀）
        // 例如 "icon-1-车.png" -> "车"
        String iconLabel = iconName.replaceAll("^icon-\\d+-", "").replaceAll("\\.png$", "");
        String tip = "拖动滑块直到出现" + iconLabel;
        return new CaptchaResult(combined, targetX, tip);
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
