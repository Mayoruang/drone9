/**
 * API配置文件 - 统一管理后端接口地址和配置
 */

// ============================================================================
// 基础配置
// ============================================================================

/** API基础URL */
export const API_BASE_URL = import.meta.env.VITE_GLOB_API_URL || 'http://localhost:8080';

/** WebSocket基础URL */
export const WS_BASE_URL = import.meta.env.VITE_GLOB_WS_URL || 'ws://localhost:8080';

/** 文件上传基础URL */
export const UPLOAD_BASE_URL = `${API_BASE_URL}/api/v1/files`;

// ============================================================================
// API端点配置
// ============================================================================

/** 地理围栏相关API端点 */
export const GEOFENCE_API = {
  /** 基础路径 */
  BASE: '/v1/geofences',
  /** 列表查询 */
  LIST: '/v1/geofences',
  /** 详情查询 */
  DETAIL: (id: string) => `/v1/geofences/${id}`,
  /** 创建 */
  CREATE: '/v1/geofences',
  /** 更新 */
  UPDATE: (id: string) => `/v1/geofences/${id}`,
  /** 删除 */
  DELETE: (id: string) => `/v1/geofences/${id}`,
  /** 绑定无人机 */
  BIND_DRONES: (id: string) => `/v1/geofences/${id}/drones`,
  /** 解绑无人机 */
  UNBIND_DRONE: (geofenceId: string, droneId: string) =>
    `/v1/geofences/${geofenceId}/drones/${droneId}`,
  /** 统计信息 */
  STATS: '/v1/geofences/stats',
  /** 连接测试 */
  TEST: '/v1/geofences/test',
} as const;

/** 无人机相关API端点 */
export const DRONE_API = {
  /** 基础路径 */
  BASE: '/v1/drones',
  /** 列表查询 */
  LIST: '/v1/drones',
  /** 详情查询 */
  DETAIL: (id: string) => `/v1/drones/${id}`,
  /** 遥测数据 */
  TELEMETRY: (id: string) => `/v1/drones/${id}/telemetry`,
  /** 批量遥测数据 */
  BATCH_TELEMETRY: '/v1/drones/telemetry/batch',
  /** 发送命令 */
  COMMAND: (id: string) => `/v1/drones/${id}/commands`,
  /** 状态更新 */
  STATUS: (id: string) => `/v1/drones/${id}/status`,
  /** 删除 */
  DELETE: (id: string) => `/v1/drones/${id}`,
  /** 统计信息 */
  STATS: '/v1/drones/stats',
  /** 连接测试 */
  TEST: '/v1/drones/test',
} as const;

/** 用户认证相关API端点 */
export const AUTH_API = {
  /** 登录 */
  LOGIN: '/auth/login',
  /** 登出 */
  LOGOUT: '/auth/logout',
  /** 刷新令牌 */
  REFRESH: '/auth/refresh',
  /** 用户信息 */
  USER_INFO: '/auth/user',
  /** 修改密码 */
  CHANGE_PASSWORD: '/auth/password',
  /** 获取权限码 */
  CODES: '/auth/codes',
} as const;

/** 注册管理相关API端点 */
export const REGISTRATION_API = {
  /** 基础路径 */
  BASE: '/v1/registration',
  /** 注册请求列表 */
  REQUESTS: '/v1/registration/requests',
  /** 处理注册请求 */
  PROCESS: (id: string) => `/v1/registration/requests/${id}/process`,
  /** 删除注册请求 */
  DELETE: (id: string) => `/v1/registration/requests/${id}`,
} as const;

/** 系统管理相关API端点 */
export const SYSTEM_API = {
  /** 系统统计 */
  STATS: '/v1/system/stats',
  /** 健康检查 */
  HEALTH: '/v1/system/health',
  /** 系统信息 */
  INFO: '/v1/system/info',
  /** 日志管理 */
  LOGS: '/v1/system/logs',
} as const;

// ============================================================================
// WebSocket端点配置
// ============================================================================

/** WebSocket端点 */
export const WS_ENDPOINTS = {
  /** 无人机遥测数据 */
  DRONE_TELEMETRY: '/ws/telemetry',
  /** 系统通知 */
  NOTIFICATIONS: '/ws/notifications',
  /** 地理围栏事件 */
  GEOFENCE_EVENTS: '/ws/geofence-events',
} as const;

// ============================================================================
// 请求配置
// ============================================================================

/** HTTP请求配置 */
export const REQUEST_CONFIG = {
  /** 请求超时时间（毫秒） */
  TIMEOUT: 10000,
  /** 重试次数 */
  RETRY_COUNT: 3,
  /** 重试延迟（毫秒） */
  RETRY_DELAY: 1000,
  /** 请求头配置 */
  HEADERS: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
} as const;

/** WebSocket配置 */
export const WS_CONFIG = {
  /** 重连间隔（毫秒） */
  RECONNECT_INTERVAL: 5000,
  /** 最大重连次数 */
  MAX_RECONNECT_ATTEMPTS: 10,
  /** 心跳间隔（毫秒） */
  HEARTBEAT_INTERVAL: 30000,
  /** 连接超时（毫秒） */
  CONNECTION_TIMEOUT: 5000,
} as const;

// ============================================================================
// 环境配置
// ============================================================================

/** 开发环境配置 */
export const DEV_CONFIG = {
  /** 是否开发环境 */
  IS_DEV: import.meta.env.DEV,
  /** 是否生产环境 */
  IS_PROD: import.meta.env.PROD,
  /** 是否启用Mock数据 */
  ENABLE_MOCK: import.meta.env.VITE_USE_MOCK === 'true',
  /** 是否启用调试 */
  ENABLE_DEBUG: import.meta.env.VITE_DEBUG === 'true',
} as const;

/** CORS配置 */
export const CORS_CONFIG = {
  /** 允许的源 */
  ALLOWED_ORIGINS: [
    'http://localhost:5173',
    'http://localhost:3000',
    'http://127.0.0.1:5173',
    'http://127.0.0.1:3000',
  ],
  /** 允许的方法 */
  ALLOWED_METHODS: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  /** 允许的头部 */
  ALLOWED_HEADERS: [
    'Content-Type',
    'Authorization',
    'X-Requested-With',
    'Accept',
    'Origin',
    'Cache-Control',
  ],
  /** 是否允许凭证 */
  ALLOW_CREDENTIALS: true,
} as const;

// ============================================================================
// 错误处理配置
// ============================================================================

/** 错误代码映射 */
export const ERROR_CODES = {
  /** 网络错误 */
  NETWORK_ERROR: 'NETWORK_ERROR',
  /** 超时错误 */
  TIMEOUT_ERROR: 'TIMEOUT_ERROR',
  /** 认证失败 */
  AUTH_FAILED: 'AUTH_FAILED',
  /** 权限不足 */
  ACCESS_DENIED: 'ACCESS_DENIED',
  /** 资源不存在 */
  NOT_FOUND: 'NOT_FOUND',
  /** 服务器错误 */
  SERVER_ERROR: 'SERVER_ERROR',
  /** 业务错误 */
  BUSINESS_ERROR: 'BUSINESS_ERROR',
  /** 验证错误 */
  VALIDATION_ERROR: 'VALIDATION_ERROR',
} as const;

/** 错误消息映射 */
export const ERROR_MESSAGES = {
  [ERROR_CODES.NETWORK_ERROR]: '网络连接失败，请检查网络设置',
  [ERROR_CODES.TIMEOUT_ERROR]: '请求超时，请稍后重试',
  [ERROR_CODES.AUTH_FAILED]: '认证失败，请重新登录',
  [ERROR_CODES.ACCESS_DENIED]: '权限不足，无法访问该资源',
  [ERROR_CODES.NOT_FOUND]: '请求的资源不存在',
  [ERROR_CODES.SERVER_ERROR]: '服务器内部错误，请联系管理员',
  [ERROR_CODES.BUSINESS_ERROR]: '业务逻辑错误',
  [ERROR_CODES.VALIDATION_ERROR]: '数据验证失败',
} as const;

// ============================================================================
// 工具函数
// ============================================================================

/**
 * 获取完整的API URL
 * @param path API路径
 * @returns 完整URL
 */
export function getApiUrl(path: string): string {
  if (path.startsWith('http')) {
    return path;
  }
  return `${API_BASE_URL}${path.startsWith('/') ? '' : '/'}${path}`;
}

/**
 * 获取完整的WebSocket URL
 * @param path WebSocket路径
 * @returns 完整URL
 */
export function getWsUrl(path: string): string {
  if (path.startsWith('ws')) {
    return path;
  }
  return `${WS_BASE_URL}${path.startsWith('/') ? '' : '/'}${path}`;
}

/**
 * 检查是否为开发环境
 * @returns 是否为开发环境
 */
export function isDevelopment(): boolean {
  return DEV_CONFIG.IS_DEV;
}

/**
 * 检查是否为生产环境
 * @returns 是否为生产环境
 */
export function isProduction(): boolean {
  return DEV_CONFIG.IS_PROD;
}

/**
 * 检查是否启用Mock数据
 * @returns 是否启用Mock
 */
export function isMockEnabled(): boolean {
  return DEV_CONFIG.ENABLE_MOCK;
}

/**
 * 构建查询参数字符串
 * @param params 参数对象
 * @returns 查询字符串
 */
export function buildQueryString(params: Record<string, any>): string {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      if (Array.isArray(value)) {
        value.forEach(item => searchParams.append(key, String(item)));
      } else {
        searchParams.append(key, String(value));
      }
    }
  });

  return searchParams.toString();
}

// ============================================================================
// 默认导出
// ============================================================================

export default {
  API_BASE_URL,
  WS_BASE_URL,
  UPLOAD_BASE_URL,
  GEOFENCE_API,
  DRONE_API,
  AUTH_API,
  REGISTRATION_API,
  SYSTEM_API,
  WS_ENDPOINTS,
  REQUEST_CONFIG,
  WS_CONFIG,
  DEV_CONFIG,
  CORS_CONFIG,
  ERROR_CODES,
  ERROR_MESSAGES,
  getApiUrl,
  getWsUrl,
  isDevelopment,
  isProduction,
  isMockEnabled,
  buildQueryString,
};
