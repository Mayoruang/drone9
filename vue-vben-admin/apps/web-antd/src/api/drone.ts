import { requestClient } from '#/api/request';
import { DRONE_API } from '#/config/api';

// ============================================================================
// 数据类型定义
// ============================================================================

type UUID = string;
type Timestamp = string;

// 无人机状态枚举
type DroneStatus = 'OFFLINE' | 'ONLINE' | 'FLYING' | 'IDLE' | 'ERROR' | 'LOW_BATTERY' | 'TRAJECTORY_ERROR';

// 飞行模式枚举
type FlightMode = 'MANUAL' | 'AUTO' | 'GUIDED' | 'STABILIZE' | 'LAND' | 'RTL' | 'HOLD';

// 无人机位置信息
interface DronePosition {
  latitude: number;
  longitude: number;
  altitude: number;
}

// 无人机基础信息
interface DroneInfo {
  droneId: UUID;
  serialNumber: string;
  model: string;
  status: DroneStatus;
  approvedAt: Timestamp;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

// 无人机遥测数据
interface DroneTelemetry {
  droneId: UUID;
  timestamp: Timestamp;
  batteryLevel: number;
  batteryVoltage: number;
  position: DronePosition;
  speed: number;
  heading: number;
  satellites: number;
  signalStrength: number;
  flightMode: FlightMode;
  temperature: number;
  geofenceStatus?: string;
  isGeofenceEnabled: boolean;
}

// 无人机完整数据（包含遥测数据）
interface DroneData extends DroneInfo {
  batteryPercentage: number;
  position: DronePosition;
  speed: number;
  lastHeartbeat: Timestamp;
  flightMode?: FlightMode;
  offlineAt?: Timestamp;
  offlineReason?: string;
  offlineBy?: string;
  lastFarewellMessage?: string;
  offlineNotificationSent?: boolean;
  geofenceIds?: UUID[];
  mqtt?: {
    username: string;
    topicTelemetry: string;
    topicCommands: string;
  };
}

// 无人机状态统计
interface DroneStatusStats {
  statusCounts: Record<DroneStatus, number>;
  total: number;
  online: number;
  offline: number;
  lowBattery: number;
}

// API响应格式
interface ApiResponse<T = any> {
  code: number;
  message?: string;
  data?: T;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

interface OperationResponse {
  success: boolean;
  message: string;
  resourceId?: UUID;
}

// ============================================================================
// API接口函数
// ============================================================================

/**
 * 获取所有无人机列表
 * @returns 无人机数据数组
 */
export async function getAllDrones(): Promise<DroneData[]> {
  try {
    const response = await requestClient.get<ApiResponse<DroneData[]>>(DRONE_API.LIST);
    return response.data || [];
  } catch (error) {
    console.error('获取无人机列表失败:', error);
    return [];
  }
}

/**
 * 获取无人机分页列表
 * @param page 页码
 * @param size 每页大小
 * @returns 分页响应
 */
export async function getDroneList(page = 0, size = 20): Promise<PageResponse<DroneData>> {
  const response = await requestClient.get<ApiResponse<PageResponse<DroneData>>>(
    DRONE_API.LIST,
    { params: { page, size } }
  );
  return response.data!;
}

/**
 * 获取无人机详细信息
 * @param droneId 无人机ID
 * @returns 无人机详情
 */
export async function getDroneDetail(droneId: UUID): Promise<DroneData> {
  const response = await requestClient.get<ApiResponse<DroneData>>(DRONE_API.DETAIL(droneId));
  return response.data!;
}

/**
 * 获取无人机遥测数据
 * @param droneId 无人机ID
 * @returns 遥测数据
 */
export async function getDroneTelemetry(droneId: UUID): Promise<DroneTelemetry> {
  const response = await requestClient.get<ApiResponse<DroneTelemetry>>(
    DRONE_API.TELEMETRY(droneId)
  );
  return response.data!;
}

/**
 * 批量获取无人机遥测数据
 * @param droneIds 无人机ID列表
 * @returns 遥测数据数组
 */
export async function getBatchDroneTelemetry(droneIds: UUID[]): Promise<DroneTelemetry[]> {
  const response = await requestClient.post<ApiResponse<DroneTelemetry[]>>(
    DRONE_API.BATCH_TELEMETRY,
    { droneIds }
  );
  return response.data || [];
}

/**
 * 发送无人机命令
 * @param droneId 无人机ID
 * @param command 命令内容
 * @returns 操作响应
 */
export async function sendDroneCommand(droneId: UUID, command: {
  type: string;
  parameters?: Record<string, any>;
}): Promise<OperationResponse> {
  const response = await requestClient.post<ApiResponse<OperationResponse>>(
    DRONE_API.COMMAND(droneId),
    command
  );
  return response.data!;
}

/**
 * 更新无人机状态
 * @param droneId 无人机ID
 * @param status 新状态
 * @param reason 原因（可选）
 * @returns 操作响应
 */
export async function updateDroneStatus(
  droneId: UUID,
  status: DroneStatus,
  reason?: string
): Promise<OperationResponse> {
  const response = await requestClient.put<ApiResponse<OperationResponse>>(
    DRONE_API.STATUS(droneId),
    { status, reason }
  );
  return response.data!;
}

/**
 * 获取无人机状态统计
 * @returns 状态统计
 */
export async function getDroneStats(): Promise<DroneStatusStats> {
  const response = await requestClient.get<ApiResponse<DroneStatusStats>>(DRONE_API.STATS);
  return response.data!;
}

/**
 * 删除无人机
 * @param droneId 无人机ID
 * @returns 操作响应
 */
export async function deleteDrone(droneId: UUID): Promise<OperationResponse> {
  const response = await requestClient.delete<ApiResponse<OperationResponse>>(
    DRONE_API.DELETE(droneId)
  );
  return response.data!;
}

/**
 * 测试无人机API连接
 * @returns 连接状态
 */
export async function testDroneAPI(): Promise<boolean> {
  try {
    const response = await requestClient.get<string>(DRONE_API.TEST);
    return response === 'Drone controller is working!';
  } catch (error) {
    console.error('无人机API测试失败:', error);
    return false;
  }
}

// ============================================================================
// 数据处理工具函数
// ============================================================================

/**
 * 格式化无人机状态
 * @param status 状态
 * @returns 格式化后的状态信息
 */
export function formatDroneStatus(status: DroneStatus): {
  text: string;
  color: string;
  icon: string;
} {
  const statusMap: Record<DroneStatus, { text: string; color: string; icon: string }> = {
    'ONLINE': { text: '在线', color: '#52c41a', icon: '🟢' },
    'OFFLINE': { text: '离线', color: '#8c8c8c', icon: '⚫' },
    'FLYING': { text: '飞行中', color: '#1890ff', icon: '🚁' },
    'IDLE': { text: '空闲', color: '#faad14', icon: '⭕' },
    'ERROR': { text: '错误', color: '#ff4d4f', icon: '❌' },
    'LOW_BATTERY': { text: '低电量', color: '#fa8c16', icon: '🔋' },
    'TRAJECTORY_ERROR': { text: '轨迹错误', color: '#f5222d', icon: '⚠️' },
  };

  return statusMap[status] || { text: '未知', color: '#8c8c8c', icon: '❓' };
}

/**
 * 格式化飞行模式
 * @param mode 飞行模式
 * @returns 格式化后的模式信息
 */
export function formatFlightMode(mode: FlightMode): {
  text: string;
  color: string;
  description: string;
} {
  const modeMap: Record<FlightMode, { text: string; color: string; description: string }> = {
    'MANUAL': { text: '手动', color: '#722ed1', description: '手动控制模式' },
    'AUTO': { text: '自动', color: '#13c2c2', description: '自动飞行模式' },
    'GUIDED': { text: '引导', color: '#52c41a', description: '引导飞行模式' },
    'STABILIZE': { text: '稳定', color: '#1890ff', description: '姿态稳定模式' },
    'LAND': { text: '降落', color: '#faad14', description: '自动降落模式' },
    'RTL': { text: '返航', color: '#fa541c', description: '自动返航模式' },
    'HOLD': { text: '悬停', color: '#eb2f96', description: '位置保持模式' },
  };

  return modeMap[mode] || { text: '未知', color: '#8c8c8c', description: '未知模式' };
}

/**
 * 计算电池健康状态
 * @param batteryLevel 电池电量百分比
 * @param batteryVoltage 电池电压
 * @returns 健康状态信息
 */
export function calculateBatteryHealth(batteryLevel: number, batteryVoltage: number): {
  level: 'good' | 'warning' | 'critical';
  color: string;
  text: string;
} {
  if (batteryLevel < 20 || batteryVoltage < 3.3) {
    return { level: 'critical', color: '#ff4d4f', text: '严重' };
  } else if (batteryLevel < 40 || batteryVoltage < 3.6) {
    return { level: 'warning', color: '#faad14', text: '警告' };
  } else {
    return { level: 'good', color: '#52c41a', text: '良好' };
  }
}

/**
 * 格式化信号强度
 * @param signalStrength 信号强度
 * @returns 格式化后的信号信息
 */
export function formatSignalStrength(signalStrength: number): {
  level: string;
  color: string;
  bars: number;
} {
  if (signalStrength >= 80) {
    return { level: '强', color: '#52c41a', bars: 4 };
  } else if (signalStrength >= 60) {
    return { level: '中', color: '#faad14', bars: 3 };
  } else if (signalStrength >= 40) {
    return { level: '弱', color: '#fa8c16', bars: 2 };
  } else {
    return { level: '很弱', color: '#ff4d4f', bars: 1 };
  }
}

// ============================================================================
// 导出类型
// ============================================================================

export type {
  DroneStatus,
  FlightMode,
  DronePosition,
  DroneInfo,
  DroneTelemetry,
  DroneData,
  DroneStatusStats,
};
