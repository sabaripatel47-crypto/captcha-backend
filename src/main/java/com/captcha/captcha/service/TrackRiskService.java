package com.captcha.captcha.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 滑块拖动轨迹的风控检测
 *
 * 通过分析用户拖动滑块产生的轨迹数据（List[TrackPoint]），识别以下机器人行为模式：
 * 1. 拖动耗时过短（< 500ms）—— 机器拖动通常极快
 * 2. 坐标跳跃（相邻两点 X 坐标差 > 50px）—— 正常拖动不会出现瞬移
 * 3. 速度过于均匀（标准差 < 平均步长 × 5%）—— 机器人拖动速度高度一致，人类拖动有快有慢
 *
 * 若命中任一规则，视为风险行为（机器人），返回 true
 */
@Service
public class TrackRiskService {

    /** 拖动耗时阈值：低于此值视为异常（毫秒） */
    private static final long MIN_DURATION_MS = 500;
    /** 坐标跳跃阈值：相邻轨迹点 X 坐标差超过此值视为异常（像素） */
    private static final int MAX_JUMP_PX = 50;

    /**
     * 轨迹风控检测的入口方法。
     *
     * @param track 前端传来的轨迹数据，格式为 List[{x, t}]，其中 x 为滑块位置，t 为相对起始时间的毫秒数
     * @param offsetX  前端提交的最终滑块位置（X 坐标）
     * @param targetX  后端存储的正确目标位置
     * @return true 表示命中风控（机器人行为），false 表示正常人类行为
     */
    public boolean checkRisk(Object track, int offsetX, int targetX) {
        // 非列表类型,直接返回失败
        if (!(track instanceof List)) {
            return false;
        }
        // 将轨迹数据转为map类型
        List<Map<String, Object>> trackList = (List<Map<String, Object>>) track;

        // 轨迹点数量小于2,排除这种情况
        if (trackList.size() < 2) {
            return false;
        }

        // 规则 1：拖动耗时过短
        if (isDurationTooShort(trackList)) {
            return true;
        }

        // 规则 2：坐标跳跃
        if (hasCoordinateTeleport(trackList)) {
            return true;
        }

        // 规则 3：速度过于均匀
        if (isSpeedTooUniform(trackList)) {
            return true;
        }

        return false;
    }

    /**
     * 规则 1 检测：拖动总耗时是否过短
     */
    private boolean isDurationTooShort(List<Map<String, Object>> track) {
        //获取第一个轨迹点的时间
        long t0 = ((Number) track.get(0).get("t")).longValue();//longValue转为long类型
        //获取最后一个轨迹点的时间
        long tn = ((Number) track.get(track.size() - 1).get("t")).longValue();
        //计算拖动总耗时,如果过短,返回true
        return (tn - t0) < MIN_DURATION_MS;
    }

    /**
     * 规则 2 检测：是否存在坐标跳跃
     */
    private boolean hasCoordinateTeleport(List<Map<String, Object>> track) {
        for (int i = 1; i < track.size(); i++) {
            int x0 = ((Number) track.get(i - 1).get("x")).intValue();//计算上一个轨迹点的x坐标
            int x1 = ((Number) track.get(i).get("x")).intValue();//计算当前轨迹点的x坐标
            //两个轨迹点间距过大,说明滑动太快,返回true
            if (Math.abs(x1 - x0) > MAX_JUMP_PX) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规则 3 检测：速度（步长）是否过于均匀
     */
    private boolean isSpeedTooUniform(List<Map<String, Object>> track) {
        //轨迹点数量小于5,排除这种情况
        if (track.size() < 5) {
            return false;
        }

        double totalX = 0;
        for (int i = 1; i < track.size(); i++) {
            int x0 = ((Number) track.get(i - 1).get("x")).intValue();
            int x1 = ((Number) track.get(i).get("x")).intValue();
            totalX += Math.abs(x1 - x0);
        }
        // 总移动距离过小（如用户犹豫不决,原地来回拖动）,排除这种情况
        if (totalX < 10) {
            return false;
        }
        // 平均步长过小,排除这种情况
        double avgStep = totalX / (track.size() - 1);
        if (avgStep < 1) {
            return false;
        }

        double varianceSum = 0;
        for (int i = 1; i < track.size(); i++) {
            int x0 = ((Number) track.get(i - 1).get("x")).intValue();
            int x1 = ((Number) track.get(i).get("x")).intValue();
            double step = Math.abs(x1 - x0);
            //计算每个步长和平均值的差然后平方
            varianceSum += Math.pow(step - avgStep, 2);
        }
        //计算最后得到的标准差,如果小于平均值的5%,返回true,如果速度均匀,方差和标准差都是0
        double stdDev = Math.sqrt(varianceSum / (track.size() - 1));

        return stdDev < avgStep * 0.05;
    }
}
