import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { notification } from 'ant-design-vue';
import axios from 'axios';

// 无人机状态类型
export type DroneStatus = 'FLYING' | 'IDLE' | 'LOW_BATTERY' | 'TRAJECTORY_ERROR' | 'OFFLINE' | 'ONLINE' | 'ERROR';

// 无人机数据接口
export interface DroneData {
  droneId: string;
  serialNumber: string;
  model: string;
  status: DroneStatus;
  batteryPercentage: number;
  position: {
    latitude: number;
    longitude: number;
    altitude: number;
  };
  speed: number;
  lastHeartbeat: string;
  mqtt?: {
    username: string;
    topicTelemetry: string;
    topicCommands: string;
  };
  flightMode?: string;
  offlineAt?: string;
  offlineReason?: string;
  offlineBy?: string;
  lastFarewellMessage?: string;
  // 新增字段: 是否已发送过离线通知
  offlineNotificationSent?: boolean;
}

// WebSocket遥测数据接口
export interface TelemetryData {
  droneId: string;
  timestamp: string;
  batteryLevel: number;
  batteryVoltage: number;
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number;
  heading: number;
  satellites: number;
  signalStrength: number;
  flightMode: string;
  temperature: number;
  geofenceStatus?: string;
  isGeofenceEnabled?: boolean;
  status?: DroneStatus;
  lastUpdated?: string;
  lastHeartbeat?: string;
}

// 状态对应的颜色
export const statusColors = {
  FLYING: '#1890ff',      // 蓝色 - 正常执行任务
  IDLE: '#52c41a',        // 绿色 - 地面待命
  LOW_BATTERY: '#faad14', // 黄色 - 低电量警告
  TRAJECTORY_ERROR: '#ff4d4f', // 红色 - 轨迹异常警告
  OFFLINE: '#d9d9d9',     // 灰色 - 离线
  ONLINE: '#52c41a',      // 绿色 - 在线
  ERROR: '#ff4d4f'        // 红色 - 错误
};

// 状态对应的中文描述
export const statusText = {
  FLYING: '飞行中',
  IDLE: '地面待命',
  LOW_BATTERY: '低电量警告',
  TRAJECTORY_ERROR: '轨迹异常警告',
  OFFLINE: '离线',
  ONLINE: '在线',
  ERROR: '错误'
};

export const useDroneStore = defineStore('drone', () => {
  // 存储所有无人机数据的记录
  const drones = ref<Record<string, DroneData>>({});
  // 当前选中的无人机
  const selectedDroneId = ref<string | null>(null);
  // 后端API URL
  const backendApiUrl = ref('http://localhost:8080');
  // WebSocket连接状态
  const websocketConnected = ref(false);
  // 加载状态
  const loading = ref(false);

  // 获取活跃的无人机列表
  const activeDrones = computed(() => {
    return Object.values(drones.value);
  });

  // 获取当前选中的无人机
  const selectedDrone = computed(() => {
    if (!selectedDroneId.value) return null;
    return drones.value[selectedDroneId.value] || null;
  });

  // 设置后端API URL
  function setBackendApiUrl(url: string) {
    backendApiUrl.value = url;
  }

  // 设置WebSocket连接状态
  function setWebsocketConnected(status: boolean) {
    websocketConnected.value = status;
  }

  // 更新无人机数据
  function updateDrone(droneData: Partial<DroneData> & { droneId: string }) {
    const droneId = droneData.droneId;

    // 如果是新无人机，创建完整记录
    if (!drones.value[droneId]) {
      drones.value[droneId] = {
        droneId,
        serialNumber: droneData.serialNumber || droneId,
        model: droneData.model || 'Unknown',
        status: droneData.status || 'OFFLINE',
        batteryPercentage: droneData.batteryPercentage || 0,
        position: {
          latitude: droneData.position?.latitude || 0,
          longitude: droneData.position?.longitude || 0,
          altitude: droneData.position?.altitude || 0
        },
        speed: droneData.speed || 0,
        lastHeartbeat: droneData.lastHeartbeat || new Date().toISOString(),
        flightMode: droneData.flightMode,
        offlineNotificationSent: false
      };

      // 新无人机通知
      notification.success({
        message: '检测到新无人机',
        description: `已连接到无人机: ${droneId}`,
        duration: 3
      });
    } else {
      // 更新现有无人机数据
      const drone = drones.value[droneId];

      // 如果无人机恢复在线状态，重置通知标志
      if (droneData.status && droneData.status !== 'OFFLINE' && drone.status === 'OFFLINE') {
        drone.offlineNotificationSent = false;
      }

      // 更新提供的字段
      if (droneData.serialNumber) drone.serialNumber = droneData.serialNumber;
      if (droneData.model) drone.model = droneData.model;
      if (droneData.status) drone.status = droneData.status;
      if (droneData.batteryPercentage !== undefined) drone.batteryPercentage = droneData.batteryPercentage;
      if (droneData.speed !== undefined) drone.speed = droneData.speed;
      if (droneData.lastHeartbeat) drone.lastHeartbeat = droneData.lastHeartbeat;
      if (droneData.flightMode) drone.flightMode = droneData.flightMode;

      // 更新位置信息
      if (droneData.position) {
        if (droneData.position.latitude !== undefined) drone.position.latitude = droneData.position.latitude;
        if (droneData.position.longitude !== undefined) drone.position.longitude = droneData.position.longitude;
        if (droneData.position.altitude !== undefined) drone.position.altitude = droneData.position.altitude;
      }

      // 更新离线信息
      if (droneData.status === 'OFFLINE') {
        if (droneData.offlineAt) drone.offlineAt = droneData.offlineAt;
        if (droneData.offlineReason) drone.offlineReason = droneData.offlineReason;
        if (droneData.offlineBy) drone.offlineBy = droneData.offlineBy;
        if (droneData.lastFarewellMessage) drone.lastFarewellMessage = droneData.lastFarewellMessage;
      }
    }
  }

  // 根据遥测数据更新无人机
  function updateDroneFromTelemetry(telemetry: TelemetryData) {
    if (!telemetry.droneId) {
      console.error('收到无效的无人机数据，缺少droneId', telemetry);
      return;
    }

    const droneId = telemetry.droneId;

    // 准备要更新的数据
    const updateData: Partial<DroneData> & { droneId: string } = {
      droneId,
      lastHeartbeat: telemetry.timestamp || telemetry.lastUpdated || telemetry.lastHeartbeat || new Date().toISOString(),
      position: {
        latitude: telemetry.latitude || 0,
        longitude: telemetry.longitude || 0,
        altitude: telemetry.altitude || 0
      },
      batteryPercentage: telemetry.batteryLevel || 0,
      speed: telemetry.speed || 0,
      flightMode: telemetry.flightMode
    };

    // 如果有状态信息，则直接使用
    if (telemetry.status) {
      updateData.status = telemetry.status;
    }
    // 否则，根据遥测数据推断状态
    else if (drones.value[droneId]) {
      // 保持现有状态，除非有明确的条件需要改变
      const currentStatus = drones.value[droneId].status;

      // 低电量警告
      if (telemetry.flightMode === 'LOW_BATTERY' || telemetry.batteryLevel <= 20) {
        updateData.status = 'LOW_BATTERY';
      }
      // 轨迹异常或围栏突破警告
      else if (telemetry.flightMode === 'TRAJECTORY_ERROR' || telemetry.flightMode === 'FENCE_BREACH') {
        updateData.status = 'TRAJECTORY_ERROR';
      }
      // 离线状态
      else if (telemetry.flightMode === 'OFFLINE' || (telemetry.signalStrength !== undefined && telemetry.signalStrength < 30)) {
        updateData.status = 'OFFLINE';
      }
      // 地面待命
      else if (telemetry.flightMode === 'IDLE') {
        updateData.status = 'IDLE';
      }
      // 飞行状态
      else if (telemetry.flightMode === 'FLYING' || telemetry.flightMode === 'CRUISE' || telemetry.flightMode === 'HOVER') {
        updateData.status = 'FLYING';
      }
      // 保持当前状态
      else {
        updateData.status = currentStatus;
      }
    }

    // 更新无人机数据
    updateDrone(updateData);
  }

  // 删除无人机
  function removeDrone(droneId: string) {
    if (drones.value[droneId]) {
      // 如果当前选中的是被删除的无人机，清除选择
      if (selectedDroneId.value === droneId) {
        selectedDroneId.value = null;
      }

      // 删除无人机
      delete drones.value[droneId];

      // 通知用户
      notification.info({
        message: '无人机已从系统中删除',
        description: `无人机ID: ${droneId} 已从系统中移除`,
        duration: 3
      });
    }
  }

  // 选择无人机
  function selectDrone(droneId: string | null) {
    selectedDroneId.value = droneId;
  }

  // 检查过期数据
  function checkStaleData() {
    const now = new Date().getTime();

    Object.keys(drones.value).forEach(droneId => {
      const drone = drones.value[droneId];
      if (drone && drone.lastHeartbeat) {
        const lastUpdateTime = new Date(drone.lastHeartbeat).getTime();
        const timeDiff = now - lastUpdateTime;

        // 超过30秒未更新的无人机标记为离线
        if (timeDiff > 30000 && drone.status !== 'OFFLINE') {
          // 设置为离线状态
          drone.status = 'OFFLINE';
          drone.offlineReason = '通信超时';
          drone.offlineAt = new Date().toISOString();

          // 检查是否已经发送过通知
          if (!drone.offlineNotificationSent) {
            notification.warning({
              message: '无人机已离线',
              description: `无人机 ${droneId} 已停止发送数据，标记为离线`,
              duration: 3
            });

            // 标记为已发送过通知
            drone.offlineNotificationSent = true;
          }
        }
      }
    });
  }

  // 将无人机设置为离线
  async function setDroneOffline(droneId: string, reason: string, gracePeriodSeconds: number = 10) {
    try {
      loading.value = true;

      const response = await fetch(`${backendApiUrl.value}/api/v1/drones/management/offline`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ?
            { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify({
          droneId,
          reason,
          gracePeriodSeconds
        })
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.message || `操作失败: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();

      if (data.success) {
        // 更新本地数据
        const drone = drones.value[droneId];
        if (drone) {
          drone.status = 'OFFLINE';
          drone.offlineReason = reason;
          drone.offlineAt = new Date().toISOString();
          drone.offlineNotificationSent = true; // 标记为已发送通知
        }

        notification.success({
          message: '无人机下线成功',
          description: data.message
        });

        return true;
      } else {
        notification.error({
          message: '无人机下线失败',
          description: data.message
        });
        return false;
      }
    } catch (error) {
      console.error('下线无人机出错:', error);
      notification.error({
        message: '无人机下线失败',
        description: error instanceof Error ? error.message : '操作过程中发生错误'
      });
      return false;
    } finally {
      loading.value = false;
    }
  }

  return {
    drones,
    selectedDroneId,
    backendApiUrl,
    websocketConnected,
    loading,
    activeDrones,
    selectedDrone,
    setBackendApiUrl,
    setWebsocketConnected,
    updateDrone,
    updateDroneFromTelemetry,
    removeDrone,
    selectDrone,
    checkStaleData,
    setDroneOffline
  };
});
