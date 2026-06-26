package com.captcha.captcha.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 滑块拖动轨迹的风控检测服务。
 *
 * 通过分析用户拖动滑块产生的轨迹数据（List[TrackPoint]），识别以下机器人行为模式：
 * 1. 拖动耗时过短（< 500ms）—— 机器拖动通常极快
 * 2. 坐标跳跃（相邻两点 X 坐标差 > 50px）—— 正常拖动不会出现瞬移
 * 3. 速度过于均匀（标准差 < 平均步长 × 5%）—— 机器人拖动速度高度一致，人类拖动有快有慢
 *
 * 若命中任一规则，视为风险行为（机器人），返回 true。
 */
@Service
public class TrackRiskService {

    /** 拖动耗时阈值：低于此值视为异常（毫秒） */
    private static final long MIN_DURATION_MS = 500;
    /** 坐标跳跃阈值：相邻轨迹点 X 坐标差超过此值视为异常（像素） */
    private static final int MAX_JUMP_PX = 50;

    /** 用于将前端传来的 List<Map> 反序列化为 TrackPoint 列表 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 轨迹风控检测的入口方法。
     *
     * @param trackObj 前端传来的轨迹数据，格式为 List[{x, t}]，其中 x 为滑块位置，t 为相对起始时间的毫秒数
     * @param offsetX  前端提交的最终滑块位置（X 坐标）
     * @param targetX  后端存储的正确目标位置
     * @return true 表示命中风控（机器人行为），false 表示正常人类行为
     */
    public boolean checkRisk(Object trackObj, int offsetX, int targetX) {
        List<TrackPoint> track;
        try {
            // 将前端传来的轨迹数据（可能是 LinkedHashMap 列表）反序列化为 TrackPoint 列表
            track = parseTrack(trackObj);
        } catch (Exception e) {
            // 解析失败时放行（不阻断正常用户）
            return false;
        }

        // 轨迹点少于 2 个无法分析，直接放行
        if (track == null || track.size() < 2) {
            return false;
        }

        // 规则 1：拖动耗时过短
        if (isDurationTooShort(track)) {
            return true;
        }

        // 规则 2：坐标跳跃（机器人可能通过注入 JS 直接跳到目标位置）
        if (hasCoordinateTeleport(track)) {
            return true;
        }

        // 规则 3：速度过于均匀（机器人拖动速度恒定，人类有自然抖动）
        if (isSpeedTooUniform(track)) {
            return true;
        }

        return false;
    }

    /**
     * 将前端传来的轨迹数据（List<Map>）解析为 TrackPoint 列表。
     * 前端传回的可能是 LinkedHashMap（Jackson 反序列化结果），需要逐个提取 x 和 t 字段。
     *
     * @param trackObj 前端传来的轨迹对象（通常为 List）
     * @return 解析后的轨迹点列表，解析失败返回 null
     */
    private List<TrackPoint> parseTrack(Object trackObj) throws Exception {
        if (trackObj == null) {
            return null;
        }
        if (trackObj instanceof List) {
            // 将 trackObj 序列化为 JSON 再反序列化，实现从 List<Map> 到 List<TrackPoint> 的转换
            String json = objectMapper.writeValueAsString(trackObj);
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<TrackPoint> points = new java.util.ArrayList<>();
            for (Map<String, Object> p : raw) {
                Object xObj = p.get("x");
                Object tObj = p.get("t");
                if (xObj == null || tObj == null) continue;
                // x 和 t 可能是 Number 子类（Integer/Long/Double），也可能是字符串，统一转换为数值
                int x;
                if (xObj instanceof Number) {
                    x = ((Number) xObj).intValue();
                } else {
                    x = Integer.parseInt(xObj.toString());
                }
                long t;
                if (tObj instanceof Number) {
                    t = ((Number) tObj).longValue();
                } else {
                    t = Long.parseLong(tObj.toString());
                }
                points.add(new TrackPoint(x, t));
            }
            return points;
        }
        return null;
    }

    /**
     * 规则 1 检测：拖动总耗时是否过短。
     * 计算最后一个轨迹点的时间 - 第一个轨迹点的时间，与 MIN_DURATION_MS 比较。
     */
    private boolean isDurationTooShort(List<TrackPoint> track) {
        long duration = track.get(track.size() - 1).t - track.get(0).t;
        return duration < MIN_DURATION_MS;
    }

    /**
     * 规则 2 检测：是否存在坐标跳跃。
     * 遍历所有相邻轨迹点，计算 X 坐标差值，若任意一点差值超过 MAX_JUMP_PX 则视为跳跃。
     */
    private boolean hasCoordinateTeleport(List<TrackPoint> track) {
        for (int i = 1; i < track.size(); i++) {
            int diff = Math.abs(track.get(i).x - track.get(i - 1).x);
            if (diff > MAX_JUMP_PX) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规则 3 检测：速度（步长）是否过于均匀。
     *
     * 计算逻辑：
     * 1. 遍历轨迹点，计算每相邻两点的 X 坐标差值（步长）
     * 2. 计算所有步长的平均值
     * 3. 计算标准差（Standard Deviation）
     * 4. 若标准差 < 平均步长 × 5%，说明步长高度一致，视为机器人行为
     *
     * 注意：轨迹点过少（< 5 个）时标准差计算不准确，直接放行。
     */
    private boolean isSpeedTooUniform(List<TrackPoint> track) {
        if (track.size() < 5) {
            return false;
        }

        double totalX = 0;
        for (int i = 1; i < track.size(); i++) {
            totalX += Math.abs(track.get(i).x - track.get(i - 1).x);
        }

        // 总移动距离过小（如用户犹豫不决），不纳入检测
        if (totalX < 10) {
            return false;
        }

        double avgStep = totalX / (track.size() - 1);
        // 平均步长小于 1px 忽略（拖动极慢的情况）
        if (avgStep < 1) {
            return false;
        }

        // 计算标准差
        double varianceSum = 0;
        for (int i = 1; i < track.size(); i++) {
            double step = Math.abs(track.get(i).x - track.get(i - 1).x);
            varianceSum += Math.pow(step - avgStep, 2);
        }
        double stdDev = Math.sqrt(varianceSum / (track.size() - 1));

        // 标准差占平均步长的比例越小，说明速度越均匀，越可能是机器
        return stdDev < avgStep * 0.05;
    }

    /**
     * 轨迹点的简单封装。
     * - x：滑块在时刻 t 的 X 坐标
     * - t：相对拖动开始时刻的毫秒数（从 0 开始递增）
     */
    public static class TrackPoint {
        public int x;
        public long t;

        public TrackPoint(int x, long t) {
            this.x = x;
            this.t = t;
        }
    }
}
