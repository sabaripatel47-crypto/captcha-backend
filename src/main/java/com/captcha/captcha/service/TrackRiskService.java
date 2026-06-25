package com.captcha.captcha.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TrackRiskService {

    private static final long MIN_DURATION_MS = 500;
    private static final int MAX_JUMP_PX = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class TrackPoint {
        public int x;
        public long t;

        public TrackPoint(int x, long t) {
            this.x = x;
            this.t = t;
        }
    }

    public boolean checkRisk(Object trackObj, int offsetX, int targetX) {
        List<TrackPoint> track;
        try {
            track = parseTrack(trackObj);
        } catch (Exception e) {
            return false;
        }

        if (track == null || track.size() < 2) {
            return false;
        }

        if (isDurationTooShort(track)) {
            return true;
        }

        if (hasCoordinateTeleport(track)) {
            return true;
        }

        if (isSpeedTooUniform(track)) {
            return true;
        }

        return false;
    }

    private List<TrackPoint> parseTrack(Object trackObj) throws Exception {
        if (trackObj == null) {
            return null;
        }
        if (trackObj instanceof List) {
            String json = objectMapper.writeValueAsString(trackObj);
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<TrackPoint> points = new java.util.ArrayList<>();
            for (Map<String, Object> p : raw) {
                Object xObj = p.get("x");
                Object tObj = p.get("t");
                if (xObj == null || tObj == null) continue;
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

    private boolean isDurationTooShort(List<TrackPoint> track) {
        long duration = track.get(track.size() - 1).t - track.get(0).t;
        return duration < MIN_DURATION_MS;
    }

    private boolean hasCoordinateTeleport(List<TrackPoint> track) {
        for (int i = 1; i < track.size(); i++) {
            int diff = Math.abs(track.get(i).x - track.get(i - 1).x);
            if (diff > MAX_JUMP_PX) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpeedTooUniform(List<TrackPoint> track) {
        if (track.size() < 5) {
            return false;
        }

        double totalX = 0;
        for (int i = 1; i < track.size(); i++) {
            totalX += Math.abs(track.get(i).x - track.get(i - 1).x);
        }

        if (totalX < 10) {
            return false;
        }

        double avgStep = totalX / (track.size() - 1);
        if (avgStep < 1) {
            return false;
        }

        double varianceSum = 0;
        for (int i = 1; i < track.size(); i++) {
            double step = Math.abs(track.get(i).x - track.get(i - 1).x);
            varianceSum += Math.pow(step - avgStep, 2);
        }
        double stdDev = Math.sqrt(varianceSum / (track.size() - 1));

        return stdDev < avgStep * 0.05;
    }
}
