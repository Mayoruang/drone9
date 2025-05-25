/**
 * 统一数据类型定义 - 前后端共享的数据结构
 * 这个文件定义了整个系统中使用的核心数据类型，确保前后端数据结构的一致性
 */

// ============================================================================
// 基础数据类型
// ============================================================================

/** 时间戳字符串，ISO 8601格式 */
type Timestamp = string;

/** UUID字符串 */
type UUID = string;

/** 地理坐标点 */
interface GeoPoint {
  /** 纬度 */
  lat: number;
  /** 经度 */
  lng: number;
}

/** GeoJSON坐标格式 [lng, lat] */
type GeoJSONCoordinate = [number, number];

/** GeoJSON多边形格式 */
interface GeoJSONPolygon {
  type: 'Polygon';
  coordinates: Array<Array<GeoJSONCoordinate>>;
}

// ============================================================================
// 通用API响应类型
// ============================================================================

/** 标准API响应格式 */
interface ApiResponse<T = any> {
  /** 响应码: 0表示成功，其他表示失败 */
  code: number;
  /** 响应消息 */
  message?: string;
  /** 响应数据 */
  data?: T;
}

/** 分页响应格式 */
interface PageResponse<T> {
  /** 数据列表 */
  content: T[];
  /** 总记录数 */
  totalElements: number;
  /** 总页数 */
  totalPages: number;
  /** 每页大小 */
  size: number;
  /** 当前页码（从0开始） */
  number: number;
}

/** 操作响应格式 */
interface OperationResponse {
  /** 操作是否成功 */
  success: boolean;
  /** 操作消息 */
  message: string;
  /** 相关资源ID */
  resourceId?: UUID;
}

// ============================================================================
// 用户相关数据类型
// ============================================================================

/** 用户状态枚举 */
type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

/** 用户角色类型 */
type UserRole = 'ADMIN' | 'OPERATOR' | 'VIEWER';

/** 用户基础信息 */
interface UserInfo {
  /** 用户ID */
  id: number;
  /** 用户名 */
  username: string;
  /** 真实姓名 */
  realName?: string;
  /** 头像URL */
  avatar?: string;
  /** 用户角色列表 */
  roles: UserRole[];
  /** 主页路径 */
  homePath?: string;
  /** 用户状态 */
  status: UserStatus;
  /** 创建时间 */
  createdAt: Timestamp;
  /** 最后登录时间 */
  lastLoginTime?: Timestamp;
}

/** 登录请求 */
interface LoginRequest {
  /** 用户名 */
  username: string;
  /** 密码 */
  password: string;
}

/** 登录响应 */
interface LoginResponse {
  /** 访问令牌 */
  accessToken: string;
  /** 用户信息 */
  user: UserInfo;
}

// ============================================================================
// 无人机相关数据类型
// ============================================================================

/** 无人机状态枚举 */
type DroneStatus = 'OFFLINE' | 'ONLINE' | 'FLYING' | 'IDLE' | 'ERROR' | 'LOW_BATTERY' | 'TRAJECTORY_ERROR';

/** 飞行模式枚举 */
type FlightMode = 'MANUAL' | 'AUTO' | 'GUIDED' | 'STABILIZE' | 'LAND' | 'RTL' | 'HOLD';

/** 无人机基础信息 */
interface DroneInfo {
  /** 无人机ID */
  droneId: UUID;
  /** 序列号 */
  serialNumber: string;
  /** 型号 */
  model: string;
  /** 当前状态 */
  status: DroneStatus;
  /** 批准时间 */
  approvedAt: Timestamp;
  /** 创建时间 */
  createdAt: Timestamp;
  /** 更新时间 */
  updatedAt: Timestamp;
}

/** 无人机位置信息 */
interface DronePosition {
  /** 纬度 */
  latitude: number;
  /** 经度 */
  longitude: number;
  /** 海拔高度（米） */
  altitude: number;
}

/** 无人机遥测数据 */
interface DroneTelemetry {
  /** 无人机ID */
  droneId: UUID;
  /** 时间戳 */
  timestamp: Timestamp;
  /** 电池电量百分比 */
  batteryLevel: number;
  /** 电池电压 */
  batteryVoltage: number;
  /** 位置信息 */
  position: DronePosition;
  /** 速度（米/秒） */
  speed: number;
  /** 航向角（度） */
  heading: number;
  /** 卫星数量 */
  satellites: number;
  /** 信号强度 */
  signalStrength: number;
  /** 飞行模式 */
  flightMode: FlightMode;
  /** 温度（摄氏度） */
  temperature: number;
  /** 地理围栏状态 */
  geofenceStatus?: string;
  /** 是否启用地理围栏 */
  isGeofenceEnabled: boolean;
}

/** 无人机完整数据（包含遥测数据） */
interface DroneData extends DroneInfo {
  /** 电池电量百分比 */
  batteryPercentage: number;
  /** 当前位置 */
  position: DronePosition;
  /** 当前速度 */
  speed: number;
  /** 最后心跳时间 */
  lastHeartbeat: Timestamp;
  /** 飞行模式 */
  flightMode?: FlightMode;
  /** 离线时间 */
  offlineAt?: Timestamp;
  /** 离线原因 */
  offlineReason?: string;
  /** 操作人员 */
  offlineBy?: string;
  /** 最后告别消息 */
  lastFarewellMessage?: string;
  /** 是否已发送离线通知 */
  offlineNotificationSent?: boolean;
  /** 关联的地理围栏ID列表 */
  geofenceIds?: UUID[];
  /** MQTT配置 */
  mqtt?: {
    username: string;
    topicTelemetry: string;
    topicCommands: string;
  };
}

/** 无人机状态统计 */
interface DroneStatusStats {
  /** 各状态下的无人机数量 */
  statusCounts: Record<DroneStatus, number>;
  /** 总数量 */
  total: number;
  /** 在线数量 */
  online: number;
  /** 离线数量 */
  offline: number;
  /** 低电量数量 */
  lowBattery: number;
}

// ============================================================================
// 地理围栏相关数据类型
// ============================================================================

/** 地理围栏类型枚举 */
type GeofenceType = 'NO_FLY_ZONE' | 'FLY_ZONE';

/** 地理围栏基础信息 */
interface GeofenceInfo {
  /** 地理围栏ID */
  geofenceId: UUID;
  /** 名称 */
  name: string;
  /** 描述 */
  description?: string;
  /** 是否激活 */
  active: boolean;
  /** 创建者 */
  createdBy: string;
  /** 创建时间 */
  createdAt: Timestamp;
  /** 更新时间 */
  updatedAt: Timestamp;
}

/** 地理围栏几何信息 */
interface GeofenceGeometry {
  /** GeoJSON多边形 */
  geometry: GeoJSONPolygon;
  /** 最小高度（米） */
  altitudeMin?: number;
  /** 最大高度（米） */
  altitudeMax?: number;
  /** 生效时间 */
  startTime?: Timestamp;
  /** 失效时间 */
  endTime?: Timestamp;
  /** 缩略图URL */
  thumbnailUrl?: string;
}

/** 地理围栏列表项 */
interface GeofenceListItem extends GeofenceInfo {
  /** 中心点坐标 */
  center: [number, number];
  /** 关联的无人机数量 */
  droneCount: number;
  /** 缩略图URL */
  thumbnailUrl?: string;
}

/** 地理围栏详细信息 */
interface GeofenceDetail extends GeofenceInfo, GeofenceGeometry {
  /** 关联的无人机列表 */
  drones: Array<{
    droneId: UUID;
    serialNumber: string;
    model: string;
  }>;
}

/** 前端地理围栏数据（为了兼容现有代码） */
interface GeofenceData {
  /** ID */
  id: UUID;
  /** 名称 */
  name: string;
  /** 类型 */
  type: GeofenceType;
  /** 坐标点数组 */
  coordinates: GeoPoint[];
  /** 描述 */
  description?: string;
  /** 创建时间 */
  createTime: Timestamp;
  /** 缩略图 */
  thumbnail?: string;
  /** 关联的无人机ID列表 */
  droneIds?: UUID[];
  /** 是否激活 */
  active?: boolean;
}

/** 创建地理围栏请求 */
interface GeofenceCreateRequest {
  /** 名称 */
  name: string;
  /** 描述 */
  description?: string;
  /** 几何信息 */
  geometry: GeoJSONPolygon;
  /** 是否激活 */
  active?: boolean;
  /** 最小高度 */
  altitudeMin?: number;
  /** 最大高度 */
  altitudeMax?: number;
  /** 生效时间 */
  startTime?: Timestamp;
  /** 失效时间 */
  endTime?: Timestamp;
  /** 关联的无人机ID列表 */
  droneIds?: UUID[];
}

/** 地理围栏操作响应 */
interface GeofenceResponse extends OperationResponse {
  /** 地理围栏ID */
  geofenceId?: UUID;
}

// ============================================================================
// 注册管理相关数据类型
// ============================================================================

/** 注册状态枚举 */
type RegistrationStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'DELETED';

/** 无人机注册请求 */
interface DroneRegistrationRequest {
  /** 请求ID */
  requestId: UUID;
  /** 序列号 */
  serialNumber: string;
  /** 型号 */
  model: string;
  /** 注册状态 */
  status: RegistrationStatus;
  /** 请求时间 */
  requestedAt: Timestamp;
  /** 处理时间 */
  processedAt?: Timestamp;
  /** 管理员备注 */
  adminNotes?: string;
  /** 批准后的无人机ID */
  droneId?: UUID;
}

/** 管理员操作请求 */
interface AdminActionRequest {
  /** 操作类型 */
  action: 'APPROVE' | 'REJECT';
  /** 备注 */
  notes?: string;
}

// ============================================================================
// WebSocket相关数据类型
// ============================================================================

/** WebSocket消息类型 */
type WebSocketMessageType = 'TELEMETRY' | 'STATUS_UPDATE' | 'COMMAND' | 'NOTIFICATION';

/** WebSocket消息基础结构 */
interface WebSocketMessage<T = any> {
  /** 消息类型 */
  type: WebSocketMessageType;
  /** 时间戳 */
  timestamp: Timestamp;
  /** 消息数据 */
  data: T;
}

/** 遥测数据WebSocket消息 */
interface TelemetryMessage extends WebSocketMessage<DroneTelemetry> {
  type: 'TELEMETRY';
}

/** 状态更新WebSocket消息 */
interface StatusUpdateMessage extends WebSocketMessage<{
  droneId: UUID;
  status: DroneStatus;
  reason?: string;
}> {
  type: 'STATUS_UPDATE';
}

// ============================================================================
// 表格和分页相关数据类型
// ============================================================================

/** 排序方向 */
type SortDirection = 'ASC' | 'DESC';

/** 排序参数 */
interface SortParam {
  /** 排序字段 */
  field: string;
  /** 排序方向 */
  direction: SortDirection;
}

/** 分页参数 */
interface PageParam {
  /** 页码（从0开始） */
  page: number;
  /** 每页大小 */
  size: number;
  /** 排序参数 */
  sort?: SortParam[];
}

/** 过滤参数 */
interface FilterParam {
  /** 字段名 */
  field: string;
  /** 操作符 */
  operator: 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'LIKE' | 'IN' | 'NOT_IN';
  /** 值 */
  value: any;
}

/** 查询参数 */
interface QueryParam extends PageParam {
  /** 过滤条件 */
  filters?: FilterParam[];
  /** 搜索关键词 */
  search?: string;
}

// ============================================================================
// 系统监控相关数据类型
// ============================================================================

/** 系统统计信息 */
interface SystemStats {
  /** 无人机统计 */
  droneStats: DroneStatusStats;
  /** 地理围栏统计 */
  geofenceStats: {
    total: number;
    active: number;
    inactive: number;
  };
  /** 在线用户数 */
  onlineUsers: number;
  /** 系统负载 */
  systemLoad: {
    cpu: number;
    memory: number;
    disk: number;
  };
}

// ============================================================================
// 错误处理相关数据类型
// ============================================================================

/** 错误代码枚举 */
type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'RESOURCE_NOT_FOUND'
  | 'ACCESS_DENIED'
  | 'BUSINESS_ERROR'
  | 'SYSTEM_ERROR'
  | 'NETWORK_ERROR';

/** 错误信息 */
interface ErrorInfo {
  /** 错误代码 */
  code: ErrorCode;
  /** 错误消息 */
  message: string;
  /** 错误详情 */
  details?: any;
  /** 时间戳 */
  timestamp: Timestamp;
}

// ============================================================================
// 导出所有类型
// ============================================================================

export type {
  // 基础类型
  Timestamp,
  UUID,
  GeoPoint,
  GeoJSONCoordinate,
  GeoJSONPolygon,

  // 通用API类型
  ApiResponse,
  PageResponse,
  OperationResponse,

  // 用户相关
  UserStatus,
  UserRole,
  UserInfo,
  LoginRequest,
  LoginResponse,

  // 无人机相关
  DroneStatus,
  FlightMode,
  DroneInfo,
  DronePosition,
  DroneTelemetry,
  DroneData,
  DroneStatusStats,

  // 地理围栏相关
  GeofenceType,
  GeofenceInfo,
  GeofenceGeometry,
  GeofenceListItem,
  GeofenceDetail,
  GeofenceData,
  GeofenceCreateRequest,
  GeofenceResponse,

  // 注册管理相关
  RegistrationStatus,
  DroneRegistrationRequest,
  AdminActionRequest,

  // WebSocket相关
  WebSocketMessageType,
  WebSocketMessage,
  TelemetryMessage,
  StatusUpdateMessage,

  // 查询相关
  SortDirection,
  SortParam,
  PageParam,
  FilterParam,
  QueryParam,

  // 系统监控
  SystemStats,

  // 错误处理
  ErrorCode,
  ErrorInfo,
};
