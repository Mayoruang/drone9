package com.huang.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 测试数据控制器 - 用于生成模拟数据
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TestDataController {

    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();
    
    /**
     * 生成模拟无人机数据并通过WebSocket推送
     * @param count 无人机数量
     * @return 生成的无人机数据
     */
    @GetMapping("/generate-drones")
    public ResponseEntity<List<Map<String, Object>>> generateDroneData(
            @RequestParam(defaultValue = "5") int count) {
            
        List<Map<String, Object>> drones = new ArrayList<>();
        
        // 沈阳市中心坐标
        double baseLatitude = 41.8057;
        double baseLongitude = 123.4315;
        
        // 生成模拟无人机数据
        for (int i = 0; i < count; i++) {
            Map<String, Object> drone = new HashMap<>();
            String droneId = "TEST-DRONE-" + (i + 1);
            
            // 在基础坐标周围随机位置 (约10公里范围内)
            double latOffset = (random.nextDouble() - 0.5) * 0.1;
            double lngOffset = (random.nextDouble() - 0.5) * 0.1;
            
            drone.put("droneId", droneId);
            drone.put("serialNumber", "SIM-" + (1000 + i));
            drone.put("model", "Simulator-X" + (i % 3 + 1));
            drone.put("status", getRandomStatus());
            drone.put("latitude", baseLatitude + latOffset);
            drone.put("longitude", baseLongitude + lngOffset);
            drone.put("altitude", 100.0 + random.nextInt(200));
            drone.put("batteryLevel", random.nextInt(100));
            drone.put("speed", 5 + random.nextInt(20));
            drone.put("heading", random.nextInt(360));
            drone.put("timestamp", new Date().toInstant().toString());
            
            drones.add(drone);
        }
        
        // 推送到WebSocket主题
        messagingTemplate.convertAndSend("/topic/drones/positions", drones);
        log.info("Generated and pushed {} simulated drones to WebSocket", count);
        
        return ResponseEntity.ok(drones);
    }
    
    /**
     * 启动持续推送模拟数据（周期性）
     * @param count 无人机数量
     * @param intervalMs 推送间隔（毫秒）
     * @return 操作结果
     */
    @GetMapping("/start-simulation")
    public ResponseEntity<Map<String, Object>> startSimulation(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "2000") int intervalMs) {
            
        // 在实际应用中，这里应该启动一个定时任务而不是线程
        // 此处使用线程仅作为演示
        Thread simulationThread = new Thread(() -> {
            try {
                List<Map<String, Object>> drones = createInitialDrones(count);
                
                // 持续推送20次后停止
                for (int i = 0; i < 20; i++) {
                    // 更新无人机位置
                    updateDronePositions(drones);
                    
                    // 推送到WebSocket
                    messagingTemplate.convertAndSend("/topic/drones/positions", drones);
                    log.info("Pushed updated drone positions (iteration {})", i+1);
                    
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException e) {
                log.warn("Simulation thread interrupted", e);
            }
        });
        
        simulationThread.setDaemon(true);
        simulationThread.start();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Started simulation with " + count + " drones, interval: " + intervalMs + "ms");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 创建初始无人机集合
     */
    private List<Map<String, Object>> createInitialDrones(int count) {
        List<Map<String, Object>> drones = new ArrayList<>();
        
        // 沈阳市中心坐标
        double baseLatitude = 41.8057;
        double baseLongitude = 123.4315;
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> drone = new HashMap<>();
            String droneId = "TEST-DRONE-" + (i + 1);
            
            // 在基础坐标周围随机位置 (约10公里范围内)
            double latOffset = (random.nextDouble() - 0.5) * 0.1;
            double lngOffset = (random.nextDouble() - 0.5) * 0.1;
            
            drone.put("droneId", droneId);
            drone.put("serialNumber", "SIM-" + (1000 + i));
            drone.put("model", "Simulator-X" + (i % 3 + 1));
            drone.put("status", getRandomStatus());
            drone.put("latitude", baseLatitude + latOffset);
            drone.put("longitude", baseLongitude + lngOffset);
            drone.put("altitude", 100.0 + random.nextInt(200));
            drone.put("batteryLevel", 80 + random.nextInt(20)); // 初始电量较高
            drone.put("speed", 5 + random.nextInt(20));
            drone.put("heading", random.nextInt(360));
            drone.put("timestamp", new Date().toInstant().toString());
            
            drones.add(drone);
        }
        
        return drones;
    }
    
    /**
     * 更新无人机位置（模拟移动）
     */
    private void updateDronePositions(List<Map<String, Object>> drones) {
        for (Map<String, Object> drone : drones) {
            // 小幅度随机移动
            double latitude = (Double) drone.get("latitude");
            double longitude = (Double) drone.get("longitude");
            // 修复类型转换错误 - 使用Number作为中间类型
            Object altObj = drone.get("altitude");
            double altitude = (altObj instanceof Integer) 
                ? ((Integer) altObj).doubleValue() 
                : (Double) altObj;
            int batteryLevel = (Integer) drone.get("batteryLevel");
            
            // 更新位置 (大约100-200米的移动)
            latitude += (random.nextDouble() - 0.5) * 0.002;
            longitude += (random.nextDouble() - 0.5) * 0.002;
            altitude += (random.nextDouble() - 0.5) * 10;
            batteryLevel -= random.nextInt(2); // 略微减少电量
            
            // 防止电量低于0
            if (batteryLevel < 0) batteryLevel = 0;
            
            // 电量低于20%时更新状态为LOW_BATTERY
            if (batteryLevel < 20) {
                drone.put("status", "LOW_BATTERY");
            }
            
            // 更新值
            drone.put("latitude", latitude);
            drone.put("longitude", longitude);
            drone.put("altitude", altitude);
            drone.put("batteryLevel", batteryLevel);
            drone.put("timestamp", new Date().toInstant().toString());
        }
    }
    
    /**
     * 随机选择一个无人机状态
     */
    private String getRandomStatus() {
        String[] statuses = {"FLYING", "IDLE", "LOW_BATTERY", "TRAJECTORY_ERROR", "OFFLINE"};
        return statuses[random.nextInt(statuses.length)];
    }
} 