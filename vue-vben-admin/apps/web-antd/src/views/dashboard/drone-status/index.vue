<script lang="ts" setup>
import { ref, onMounted, onUnmounted, computed, reactive, watch } from 'vue';
import { Card, Drawer, Button, Tabs, Descriptions, Tag, Slider, Switch, Input, notification, Space, Modal, Form, Tooltip, Select, Checkbox } from 'ant-design-vue';
import { EyeOutlined, SendOutlined, EnvironmentOutlined, BarsOutlined, WarningOutlined, ClockCircleOutlined, BorderOutlined, ToolOutlined, ExperimentOutlined, DeleteOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue';
import SockJS from 'sockjs-client';
import Stomp from 'webstomp-client';
// @ts-ignore
import axios from 'axios';
// 导入地图组件
import BaiduMap from './BaiduMap.vue';
// 导入地理围栏API方法
import {
  getDroneGeofences,
  getAvailableGeofences,
  assignGeofences,
  unassignGeofence,
  updateGeofenceAssignments,
  type GeofenceListItem,
  type GeofenceAssignmentResponse,
  // 新增无人机控制相关API导入
  sendDroneControlCommand,
  sendRawCommand as apiSendRawCommand,
  emergencyStopDrone,
  returnToHome,
  landDrone,
  hoverDrone,
  getDroneCommandHistory,
  checkDroneAvailability,
  cancelDroneCommand,
  emergencyStopAll,
  createDroneCommand,
  createMoveToCommand,
  createTakeoffCommand,
  createPatrolCommand,
  createSetAltitudeCommand,
  createSetSpeedCommand,
  formatCommandStatus,
  type DroneCommand,
  type DroneAvailability,
  type DroneCommandResponse
} from '#/api/drone';
// 添加地理围栏页面的API导入
import {
  getAllGeofences,
  type GeofenceData
} from '#/api/geofence';

// 无人机状态类型
type DroneStatus = 'FLYING' | 'IDLE' | 'LOW_BATTERY' | 'TRAJECTORY_ERROR' | 'OFFLINE' | 'GEOFENCE_VIOLATION';

// 无人机数据接口
interface DroneData {
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
  mqtt: {
    username: string;
    topicTelemetry: string;
    topicCommands: string;
  };
  flightMode?: string;
  offlineAt?: string;
  offlineReason?: string;
  offlineBy?: string;
  lastFarewellMessage?: string;
}

// WebSocket遥测数据接口
interface TelemetryData {
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
  // 添加后端可能返回的其他字段
  serialNumber?: string;
  model?: string;
  status?: DroneStatus;
  lastUpdated?: string;
  lastHeartbeat?: string;
}

// 在template中使用的按钮类型定义
type ButtonType = 'primary' | 'ghost' | 'dashed' | 'link' | 'text' | 'default';
type DangerButtonType = ButtonType | 'danger';

// 初始化状态
const loading = ref(false);
const map = ref<any>(null);
const droneMarkers = ref<any[]>([]);
const geofenceOverlays = ref<any[]>([]); // 添加地理围栏覆盖物数组
const drawerVisible = ref(false);
const selectedDrone = ref<DroneData | null>(null);
const activeTabKey = ref('1');
const commandMessage = ref('');
const geofenceActive = ref(false);
const geofenceRadius = ref(500); // 默认500米
const showDroneGeofences = ref(true); // 添加控制是否显示无人机关联地理围栏的开关
const mockDrones = ref<DroneData[]>([]);
const realDrones = ref<Record<string, DroneData>>({});
const useRealData = ref(true); // 默认使用真实数据，改为true
const mapScriptLoaded = ref(false);
const mapScriptContainer = ref<HTMLDivElement | null>(null);

// 测试相关状态
const backendApiUrl = ref('http://localhost:8080');
const droneCount = ref(5);
const simulationActive = ref(false);
const simulationInterval = ref(2000);

// WebSocket客户端
const stompClient = ref<any>(null);
const connected = ref(false);

// MQTT消息模态框
const mqttModalVisible = ref(false);
const mqttForm = reactive({
  topic: '',
  message: '',
});

// State variables for offline functionality
const offlineModalVisible = ref(false);
const offlineReason = ref('');
const processingOffline = ref(false);
const offlineDroneId = ref('');

// ===== 遥控器相关状态 =====
const availability = ref<DroneAvailability | null>(null);
const commandHistory = ref<DroneCommand[]>([]);
const lastResponse = ref<string>('');

// 加载状态
const commandLoading = ref(false);
const emergencyLoading = ref(false);
const availabilityLoading = ref(false);
const historyLoading = ref(false);

// 界面状态
const controlActiveTab = ref('movement');
const takeoffDialogVisible = ref(false);
const takeoffAltitude = ref(30);

// 命令参数
const gotoParams = reactive({
  latitude: null as number | null,
  longitude: null as number | null,
  altitude: 25,
  speed: 5
});

const patrolParams = reactive({
  trajectoryType: 'RECTANGLE',
  size: 100,
  altitude: 30,
  speed: 5
});

const altitudeValue = ref<number | null>(null);
const speedValue = ref<number | null>(null);
const rawCommand = ref('{\n  "action": "LAND",\n  "parameters": {}\n}');

// 状态对应的颜色
const statusColors = {
  FLYING: '#1890ff', // 蓝色 - 正常执行任务
  IDLE: '#52c41a',   // 绿色 - 地面待命
  LOW_BATTERY: '#faad14', // 黄色 - 低电量警告
  TRAJECTORY_ERROR: '#ff4d4f', // 红色 - 轨迹异常警告
  OFFLINE: '#d9d9d9', // 灰色 - 离线
  GEOFENCE_VIOLATION: '#ff4d4f' // 红色 - 禁飞区违规
};

// 状态对应的中文描述
const statusText = {
  FLYING: '飞行中',
  IDLE: '地面待命',
  LOW_BATTERY: '低电量警告',
  TRAJECTORY_ERROR: '轨迹异常警告',
  OFFLINE: '离线',
  GEOFENCE_VIOLATION: '禁飞区违规'
};

// 获取状态标签样式
const getStatusTag = (status: DroneStatus) => {
  const color = statusColors[status];
  const text = statusText[status];
  return { color, text };
};

// 获取电池颜色
const getBatteryColor = (percentage: number) => {
  if (percentage <= 20) return '#ff4d4f';
  if (percentage <= 40) return '#faad14';
  return '#52c41a';
};

// 活跃无人机列表 - 只显示真实数据
const activeDrones = computed(() => {
  // 总是返回真实数据，忽略模拟数据
  const drones = Object.keys(realDrones.value).length > 0
    ? Object.values(realDrones.value)
    : [];
  console.log(`活跃无人机数量: ${drones.length}`);
  return drones;
});

// ===== 遥控器相关计算属性 =====
const canSendCommand = computed(() => {
  return selectedDrone.value && 
         availability.value?.available !== false && 
         !commandLoading.value;
});

const isGotoValid = computed(() => {
  return gotoParams.latitude !== null && 
         gotoParams.longitude !== null &&
         gotoParams.latitude >= -90 && gotoParams.latitude <= 90 &&
         gotoParams.longitude >= -180 && gotoParams.longitude <= 180;
});

// 计算属性用于处理null值转换
const latitudeDisplay = computed({
  get: () => gotoParams.latitude?.toString() || '',
  set: (value: string) => {
    gotoParams.latitude = value ? parseFloat(value) : null;
  }
});

const longitudeDisplay = computed({
  get: () => gotoParams.longitude?.toString() || '',
  set: (value: string) => {
    gotoParams.longitude = value ? parseFloat(value) : null;
  }
});

const altitudeDisplay = computed({
  get: () => altitudeValue.value?.toString() || '',
  set: (value: string) => {
    altitudeValue.value = value ? parseFloat(value) : null;
  }
});

const speedDisplay = computed({
  get: () => speedValue.value?.toString() || '',
  set: (value: string) => {
    speedValue.value = value ? parseFloat(value) : null;
  }
});

// ===== 遥控器工具函数 =====
const formatTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleString();
};

const formatPosition = (position: any) => {
  if (!position) return '未知';
  return `${position.latitude?.toFixed(6)}, ${position.longitude?.toFixed(6)} (${position.altitude}m)`;
};

const getDroneStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    'ONLINE': 'green',
    'FLYING': 'blue',
    'IDLE': 'cyan',
    'OFFLINE': 'red',
    'ERROR': 'red',
    'LOW_BATTERY': 'orange'
  };
  return colors[status] || 'default';
};

const getCommandStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    'PENDING': 'orange',
    'SENT': 'blue',
    'RECEIVED': 'cyan',
    'EXECUTING': 'purple',
    'COMPLETED': 'green',
    'FAILED': 'red',
    'CANCELLED': 'default',
    'TIMEOUT': 'volcano'
  };
  return colors[status] || 'default';
};

const updateResponse = (title: string, content: any) => {
  const timestamp = new Date().toLocaleString();
  lastResponse.value = `[${timestamp}] ${title}:\n${JSON.stringify(content, null, 2)}`;
};

// 调用后端生成单次无人机数据
const generateDroneData = async () => {
  try {
    loading.value = true;
    const response = await axios.get(`${backendApiUrl.value}/api/test/generate-drones`, {
      params: { count: droneCount.value }
    });

    notification.success({
      message: '生成无人机数据成功',
      description: `已生成${response.data.length}架模拟无人机并通过WebSocket推送`
    });
  } catch (error) {
    console.error('生成无人机数据失败:', error);
    notification.error({
      message: '生成无人机数据失败',
      description: '请检查后端服务是否正常运行'
    });
  } finally {
    loading.value = false;
  }
};

// 启动持续推送模拟数据
const startDroneSimulation = async () => {
  try {
    const response = await axios.get(`${backendApiUrl.value}/api/test/start-simulation`, {
      params: {
        count: droneCount.value,
        intervalMs: simulationInterval.value
      }
    });

    simulationActive.value = true;

    notification.success({
      message: '启动无人机模拟成功',
      description: response.data.message
    });

    // 20秒后自动停止模拟状态
    setTimeout(() => {
      simulationActive.value = false;
    }, 20 * simulationInterval.value);
  } catch (error) {
    console.error('启动无人机模拟失败:', error);
    notification.error({
      message: '启动无人机模拟失败',
      description: '请检查后端服务是否正常运行'
    });
  }
};

// 组件生命周期标志
const isComponentMounted = ref(true);

// 处理地图加载完成事件
const handleMapReady = (mapInstance: any) => {
  console.log('地图已准备就绪');
  map.value = mapInstance;
};

// 处理标记点击事件
const handleMarkerClick = (drone: DroneData) => {
  console.log('标记被点击', drone);
  selectedDrone.value = drone;
  drawerVisible.value = true;
  
  // 当选中无人机时，自动加载并显示其关联的地理围栏
  if (showDroneGeofences.value) {
    // 延迟加载地理围栏，确保地图已经准备好
    setTimeout(() => {
      updateDroneGeofenceDisplay();
    }, 100);
  }
};

// 修改WebSocket连接逻辑
const initWebSocket = () => {
  try {
    // 先断开已有连接
    if (stompClient.value && stompClient.value.connected) {
      stompClient.value.disconnect();
    }

    // 打印当前的WebSocket URL
    const wsUrl = `${backendApiUrl.value}/ws/drones`;
    console.log(`尝试连接WebSocket: ${wsUrl}`);

    // 使用后端正确的WebSocket端点
    const sock = new SockJS(wsUrl);
    sock.onopen = () => console.log('SockJS连接已打开');
    sock.onerror = (e) => console.error('SockJS错误:', e);
    sock.onclose = (e) => console.log('SockJS连接已关闭:', e.reason);

    stompClient.value = Stomp.over(sock);

    // 启用调试以便于排查问题
    if (process.env.NODE_ENV === 'development') {
      stompClient.value.debug = function(str: string) {
        console.log(`STOMP: ${str}`);
      };
    } else {
      stompClient.value.debug = () => {};
    }

    // 设置连接超时
    const connectTimeout = setTimeout(() => {
      if (!connected.value) {
        console.error('WebSocket连接超时');
        notification.error({
          message: 'WebSocket连接超时',
          description: '无法连接到后端WebSocket服务，将使用模拟数据'
        });
        useRealData.value = false;
      }
    }, 15000); // 15秒超时

    // 连接WebSocket服务器
    stompClient.value.connect(
      {}, // 空headers对象
      () => {
        clearTimeout(connectTimeout);
        connected.value = true;
        console.log('STOMP连接成功');

        try {
          // 首先订阅无人机位置更新主题
          console.log('订阅无人机位置主题');
          stompClient.value.subscribe('/topic/drones/positions', (message: any) => {
            if (message.body) {
              try {
                const data = JSON.parse(message.body);
                console.log('收到无人机位置数据', data);
                // 检查数据格式
                if (Array.isArray(data)) {
                  // 如果是数组，作为多个无人机处理
                  handleDronePositionUpdate(data);
                  console.log(`处理了${data.length}架无人机的数据更新`);
                } else if (typeof data === 'object' && data !== null) {
                  // 如果是单个对象，转为数组处理
                  handleDronePositionUpdate([data]);
                  console.log('处理了单架无人机的数据更新');
                } else {
                  console.error('无法识别的数据格式:', data);
                }
              } catch (e) {
                console.error('解析WebSocket消息时出错', e, message.body);
              }
            }
          });

          // 订阅无人机删除通知主题
          console.log('订阅无人机删除通知主题');
          stompClient.value.subscribe('/topic/drones/deleted', (message: any) => {
            if (message.body) {
              try {
                const data = JSON.parse(message.body);
                console.log('收到无人机删除通知', data);

                if (data.droneId) {
                  // 处理无人机删除
                  handleDroneDeleted(data.droneId);
                }
              } catch (e) {
                console.error('解析WebSocket删除通知时出错', e, message.body);
              }
            }
          });

          // 连接成功后，请求数据
          setTimeout(() => {
            if (stompClient.value && stompClient.value.connected) {
              try {
                console.log('请求无人机位置数据');
                stompClient.value.send('/app/requestDronesData', {}, JSON.stringify({}));
              } catch (e) {
                console.error('发送位置请求出错', e);
              }
            }
          }, 2000);

          // 设置定时请求，每5秒请求一次最新位置
          const positionInterval = setInterval(() => {
            if (stompClient.value && stompClient.value.connected) {
              try {
                stompClient.value.send('/app/requestDronesData', {}, JSON.stringify({}));
              } catch (e) {
                console.error('发送定时位置请求出错', e);
                clearInterval(positionInterval);
              }
            } else {
              clearInterval(positionInterval);
            }
          }, 5000);
        } catch (e) {
          console.error('设置WebSocket订阅时出错', e);
        }

        notification.success({
          message: 'WebSocket连接成功',
          description: '已开始接收无人机实时数据'
        });
      },
      (error: any) => {
        clearTimeout(connectTimeout);
        console.error('WebSocket连接失败:', error);
        connected.value = false;

        notification.error({
          message: 'WebSocket连接失败',
          description: '无法接收实时数据，将使用模拟数据'
        });
        useRealData.value = false;
      }
    );
  } catch (e) {
    console.error('WebSocket初始化失败:', e);
    useRealData.value = false;

    notification.error({
      message: 'WebSocket初始化失败',
      description: e instanceof Error ? e.message : '未知错误'
    });
  }
};

// 处理从WebSocket接收的无人机位置更新
const handleDronePositionUpdate = (positions: TelemetryData[]) => {
  if (!positions || positions.length === 0) {
    console.log('没有接收到无人机位置数据');
    return;
  }

  // 确保positions是数组
  const positionsArray = Array.isArray(positions) ? positions : [positions];
  console.log(`处理${positionsArray.length}架无人机的位置数据:`, positionsArray.map(p => p.droneId).join(', '));

  // 记录当前时间，用于检测停止发送数据的无人机
  const now = new Date().toISOString();

  // 标记已收到更新的无人机
  const updatedDroneIds = new Set<string>();

  positionsArray.forEach(data => {
    // 检查是否已有此无人机的记录
    const droneId = data.droneId?.toString();
    if (!droneId) {
      console.error('收到无效的无人机数据，缺少droneId', data);
      return;
    }

    // 🔍 添加电量数据调试日志
    console.log(`🔋 无人机 ${droneId} 电量数据检查:`, {
      batteryLevel: data.batteryLevel,
      batteryLevel_type: typeof data.batteryLevel,
      raw_data: data
    });

    updatedDroneIds.add(droneId);

    // 使用后端提供的时间戳或当前时间作为备用
    const lastHeartbeat = data.timestamp || data.lastUpdated || data.lastHeartbeat || now;

    if (!realDrones.value[droneId]) {
      // 创建新的无人机记录
      console.log(`添加新无人机: ${droneId}`, data);
      realDrones.value[droneId] = {
        droneId: droneId,
        serialNumber: data.serialNumber || droneId,
        model: data.model || 'Unknown Model',
        status: data.status || 'FLYING',
        batteryPercentage: data.batteryLevel || 0,
        position: {
          latitude: data.latitude || 0,
          longitude: data.longitude || 0,
          altitude: data.altitude || 0,
        },
        speed: data.speed || 0,
        lastHeartbeat: lastHeartbeat,
        mqtt: {
          username: '',
          topicTelemetry: `drones/${droneId}/telemetry`,
          topicCommands: `drones/${droneId}/commands`,
        },
        flightMode: data.flightMode || 'UNKNOWN'
      };

      // 🔍 添加新创建无人机的电量日志
      console.log(`✅ 新无人机 ${droneId} 初始电量: ${realDrones.value[droneId].batteryPercentage}%`);

      // 新无人机通知
      notification.success({
        message: '检测到新无人机',
        description: `已连接到无人机: ${droneId}`,
        duration: 3
      });
    } else {
      // 更新现有无人机记录
      const drone = realDrones.value[droneId];
      const oldBattery = drone.batteryPercentage;

      // 更新位置和遥测数据
      if (data.latitude !== undefined) drone.position.latitude = data.latitude;
      if (data.longitude !== undefined) drone.position.longitude = data.longitude;
      if (data.altitude !== undefined) drone.position.altitude = data.altitude;
      if (data.speed !== undefined) drone.speed = data.speed;
      if (data.batteryLevel !== undefined) {
        drone.batteryPercentage = data.batteryLevel;
        // 🔍 添加电量更新日志
        console.log(`🔋 无人机 ${droneId} 电量更新: ${oldBattery}% → ${drone.batteryPercentage}%`);
      }
      drone.lastHeartbeat = lastHeartbeat;
      if (data.flightMode) drone.flightMode = data.flightMode;

      // 优先使用后端提供的状态
      if (data.status) {
        drone.status = data.status;
      }
      // 备选：根据电池电量和telemetry数据推断状态
      else if (data.flightMode === 'LOW_BATTERY' || data.batteryLevel <= 20) {
        drone.status = 'LOW_BATTERY';
      } else if (data.flightMode === 'TRAJECTORY_ERROR') {
        drone.status = 'TRAJECTORY_ERROR';
      } else if (data.flightMode === 'FENCE_BREACH') {
        drone.status = 'TRAJECTORY_ERROR'; // 使用轨迹异常状态表示围栏突破
      } else if (data.flightMode === 'OFFLINE' || (data.signalStrength !== undefined && data.signalStrength < 30)) {
        drone.status = 'OFFLINE';
      } else if (data.flightMode === 'IDLE') {
        drone.status = 'IDLE';
      } else if (!drone.status) {
        drone.status = 'FLYING';
      }
    }
  });

  // 检查是否有无人机停止发送数据（超过30秒没有更新）
  Object.keys(realDrones.value).forEach(droneId => {
    if (!updatedDroneIds.has(droneId)) {
      const drone = realDrones.value[droneId];
      if (drone && drone.lastHeartbeat) {
        const lastUpdateTime = new Date(drone.lastHeartbeat).getTime();
        const currentTime = new Date(now).getTime();
        const timeDiff = currentTime - lastUpdateTime;

        // 如果超过30秒没有收到更新，将无人机标记为离线
        if (timeDiff > 30000 && drone.status !== 'OFFLINE') {
          console.log(`将无人机 ${droneId} 标记为离线，最后心跳时间: ${drone.lastHeartbeat}`);
          drone.status = 'OFFLINE';

          notification.warning({
            message: '无人机已离线',
            description: `无人机 ${droneId} 已停止发送数据，标记为离线`,
            duration: 3
          });
        }
      }
    }
  });

  // 显示当前跟踪的所有无人机
  console.log(`当前跟踪${Object.keys(realDrones.value).length}架无人机:`, Object.keys(realDrones.value).join(', '));

  // 触发标记更新
  renderDroneMarkers();
};

// 关闭WebSocket连接
const closeWebSocket = () => {
  if (stompClient.value && stompClient.value.connected) {
    stompClient.value.disconnect();
    connected.value = false;
    console.log('WebSocket连接已关闭');
  }
};

// 创建自定义无人机图标
const createDroneIcon = (drone: DroneData) => {
    const BMap = window.BMap;

  // 使用SVG格式的简约图标
  const svgSize = 28; // SVG尺寸
  const strokeWidth = 2; // 线条粗细

  // 根据状态选择颜色
  const color = statusColors[drone.status];

  // 创建简约风格的SVG图标
  // 生成一个简单的无人机形状SVG
  const svgIcon = `
    <svg xmlns="http://www.w3.org/2000/svg" width="${svgSize}" height="${svgSize}" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="6" stroke="${color}" stroke-width="${strokeWidth}" fill="white" />
      <circle cx="12" cy="12" r="2" fill="${color}" />
      <line x1="12" y1="2" x2="12" y2="6" stroke="${color}" stroke-width="${strokeWidth}" />
      <line x1="12" y1="18" x2="12" y2="22" stroke="${color}" stroke-width="${strokeWidth}" />
      <line x1="22" y1="12" x2="18" y2="12" stroke="${color}" stroke-width="${strokeWidth}" />
      <line x1="6" y1="12" x2="2" y2="12" stroke="${color}" stroke-width="${strokeWidth}" />
    </svg>
  `;

  // 将SVG转换为Base64编码
  const base64Icon = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svgIcon)));

  // 定义图标尺寸
  const size = new BMap.Size(svgSize, svgSize);

  // 创建图标对象
  const icon = new BMap.Icon(
    base64Icon,
    size,
    {
      imageSize: size,
      anchor: new BMap.Size(svgSize/2, svgSize/2) // 中心对齐
    }
  );

  return icon;
};

// 渲染无人机标记函数
const renderDroneMarkers = () => {
  if (!map.value) return;

  // 清除所有现有标记
  droneMarkers.value.forEach(marker => {
    map.value.removeOverlay(marker);
  });
  droneMarkers.value = [];

  // 检查是否有无人机数据
  if (activeDrones.value.length === 0) {
    console.log('没有可显示的无人机数据');
    notification.info({
      message: '等待无人机数据',
      description: '目前没有任何无人机数据。请确保Python无人机模拟器正在运行并已被管理员批准。',
      duration: 5
    });
    return;
  }

  console.log(`准备渲染${activeDrones.value.length}架无人机的标记`);

  // 注意：此处移除了自动聚焦所有无人机的逻辑，以避免地图自动平移

  // 为每个无人机创建标记
  activeDrones.value.forEach(drone => {
    const BMap = window.BMap;
    const point = new BMap.Point(drone.position.longitude, drone.position.latitude);

    // 创建标记对象
    const icon = createDroneIcon(drone);
    const labelOpts = {
      offset: new BMap.Size(20, -5) // 调整标签位置
    };

    // 创建标记
    const marker = new BMap.Marker(point, { icon });

    // 添加无人机ID标签
    const label = new BMap.Label(drone.serialNumber, labelOpts);
    label.setStyle({
      color: '#fff',
      backgroundColor: statusColors[drone.status],
      border: 'none',
      padding: '2px 6px',
      borderRadius: '3px',
      fontSize: '11px',
      fontWeight: 'bold',
      boxShadow: '0 1px 2px rgba(0,0,0,0.2)'
    });
    marker.setLabel(label);

    // 创建信息窗口内容 - 更简洁的风格
    const infoWindow = new BMap.InfoWindow(`
      <div style="width: 200px; padding: 5px; font-family: Arial, sans-serif;">
        <div style="font-weight: bold; color: ${statusColors[drone.status]}; margin-bottom: 5px; border-bottom: 1px solid #eee; padding-bottom: 3px;">
          ${drone.serialNumber} (${statusText[drone.status]})
        </div>
        <div style="font-size: 12px; line-height: 1.6; color: #333;">
          电量: ${drone.batteryPercentage}% | 高度: ${drone.position.altitude}米<br>
          速度: ${drone.speed}m/s | 模式: ${drone.flightMode || '未知'}
        </div>
      </div>
    `, {
      enableCloseOnClick: true,
      width: 0,
      height: 0
    });

    // 添加点击事件：选择无人机、打开抽屉，并将地图中心设置到该无人机位置
    marker.addEventListener('click', () => {
      // 选择无人机并打开抽屉面板
      selectedDrone.value = drone;
      drawerVisible.value = true;

      // 聚焦到选中的无人机
      centerMapOnDrone(drone);
    });

    // 添加悬停事件，显示信息窗口
    marker.addEventListener('mouseover', () => {
      marker.openInfoWindow(infoWindow);
    });

    // 添加到地图
    map.value.addOverlay(marker);

    // 保存标记引用，以便后续更新
    droneMarkers.value.push(marker);
  });

  console.log(`成功渲染了${droneMarkers.value.length}架无人机的标记`);
};

// 发送指令到无人机
const sendCommand = () => {
  if (!selectedDrone.value || !commandMessage.value) {
    notification.warning({
      message: '发送失败',
      description: '请选择无人机并输入指令信息',
    });
    return;
  }

  notification.success({
    message: '指令已发送',
    description: `已向无人机 ${selectedDrone.value.serialNumber} 发送指令: ${commandMessage.value}`,
  });

  // 清空输入
  commandMessage.value = '';
};

// ===== 遥控器API调用函数 =====

const checkAvailability = async () => {
  if (!selectedDrone.value) return;
  
  availabilityLoading.value = true;
  try {
    availability.value = await checkDroneAvailability(selectedDrone.value.droneId);
    updateResponse('可用性检查', availability.value);
  } catch (error) {
    // notification.error({ message: '检查无人机可用性失败' });
    console.error('❌ 检查无人机可用性失败:', error);
    updateResponse('可用性检查失败', error);
  } finally {
    availabilityLoading.value = false;
  }
};

const sendQuickCommand = async (action: string) => {
  if (!selectedDrone.value) {
    // notification.warning({ message: '请先选择无人机' });
    console.warn('⚠️ 请先选择无人机');
    return;
  }

  commandLoading.value = true;
  console.log(`🚀 开始发送${action}命令到无人机 ${selectedDrone.value.droneId}`);
  
  try {
    let response: DroneCommandResponse;
    
    switch (action) {
      case 'RETURN_TO_HOME':
        console.log('📡 调用returnToHome API');
        response = await returnToHome(selectedDrone.value.droneId);
        break;
      case 'LAND':
        console.log('📡 调用landDrone API');
        response = await landDrone(selectedDrone.value.droneId);
        break;
      case 'HOVER':
        console.log('📡 调用hoverDrone API');
        response = await hoverDrone(selectedDrone.value.droneId);
        break;
      case 'EMERGENCY_STOP':
        console.log('📡 调用emergencyStopDrone API');
        response = await emergencyStopDrone(selectedDrone.value.droneId);
        break;
      case 'ARM':
      case 'DISARM':
        console.log(`📡 使用通用命令API发送${action}命令`);
        const command = createDroneCommand(action as any);
        console.log(`📋 创建的命令对象:`, command);
        response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
        console.log(`📨 ${action}命令API响应:`, response);
        break;
      default:
        console.log(`📡 使用通用命令API发送${action}命令`);
        const defaultCommand = createDroneCommand(action as any);
        response = await sendDroneControlCommand(selectedDrone.value.droneId, defaultCommand);
    }
    
    console.log(`📊 ${action}命令最终响应:`, response);
    console.log(`🔍 响应详细信息: success=${response?.success} (type: ${typeof response?.success}), message="${response?.message}"`);
    
    // 检查响应的success字段 - 使用更宽松的判断条件
    const isSuccess = response && (
      response.success === true || 
      String(response.success) === 'true' || 
      (response.message && response.message.includes('成功'))
    );
    
    if (isSuccess) {
      console.log(`✅ ${action}命令发送成功`);
      // 移除notification.success调用
      updateResponse(`快速命令: ${action}`, response);
      
      // 延迟加载命令历史，避免并发请求冲突
      setTimeout(() => {
        loadCommandHistory().catch(err => {
          console.error('⚠️ 加载命令历史失败，但不影响主要功能:', err);
        });
      }, 500);
    } else {
      console.error(`❌ ${action}命令响应失败，success=${response?.success}, message=${response?.message}`);
      // 移除notification.error调用
      updateResponse(`命令失败: ${action}`, response);
    }
  } catch (error) {
    console.error(`💥 发送${action}命令时发生异常:`, error);
    // 移除notification.error调用
    updateResponse(`发送命令失败: ${action}`, error);
  } finally {
    commandLoading.value = false;
    console.log(`🏁 ${action}命令处理完成`);
  }
};

const showTakeoffDialog = () => {
  takeoffDialogVisible.value = true;
};

const confirmTakeoff = async () => {
  if (!selectedDrone.value || !takeoffAltitude.value) return;
  
  takeoffDialogVisible.value = false;
  commandLoading.value = true;
  
  try {
    const command = createTakeoffCommand(takeoffAltitude.value);
    const response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
    
    if (response.success) {
      // notification.success({ message: `起飞命令发送成功 (高度: ${takeoffAltitude.value}m)` });
      console.log(`✅ 起飞命令发送成功 (高度: ${takeoffAltitude.value}m)`);
      updateResponse('起飞命令', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `起飞命令发送失败: ${response.message}` });
      console.error(`❌ 起飞命令发送失败: ${response.message}`);
      updateResponse('起飞命令失败', response);
    }
  } catch (error) {
    // notification.error({ message: '发送起飞命令失败' });
    console.error('💥 发送起飞命令失败:', error);
    updateResponse('发送起飞命令失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const sendGotoCommand = async () => {
  if (!selectedDrone.value || !isGotoValid.value) return;
  
  commandLoading.value = true;
  try {
    const command = createMoveToCommand(
      gotoParams.latitude!,
      gotoParams.longitude!,
      gotoParams.altitude,
      gotoParams.speed
    );
    
    const response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
    
    if (response.success) {
      // notification.success({ message: 'GOTO 命令发送成功' });
      console.log('✅ GOTO 命令发送成功');
      updateResponse('GOTO 命令', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `GOTO 命令发送失败: ${response.message}` });
      console.error(`❌ GOTO 命令发送失败: ${response.message}`);
      updateResponse('GOTO 命令失败', response);
    }
  } catch (error) {
    // notification.error({ message: '发送 GOTO 命令失败' });
    console.error('💥 发送 GOTO 命令失败:', error);
    updateResponse('发送 GOTO 命令失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const setAltitude = async () => {
  if (!selectedDrone.value || !altitudeValue.value) return;
  
  commandLoading.value = true;
  try {
    const command = createSetAltitudeCommand(altitudeValue.value);
    const response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
    
    if (response.success) {
      // notification.success({ message: `设置高度成功: ${altitudeValue.value}m` });
      console.log(`✅ 设置高度成功: ${altitudeValue.value}m`);
      updateResponse('设置高度', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `设置高度失败: ${response.message}` });
      console.error(`❌ 设置高度失败: ${response.message}`);
      updateResponse('设置高度失败', response);
    }
  } catch (error) {
    // notification.error({ message: '设置高度失败' });
    console.error('💥 设置高度失败:', error);
    updateResponse('设置高度失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const setSpeed = async () => {
  if (!selectedDrone.value || !speedValue.value) return;
  
  commandLoading.value = true;
  try {
    const command = createSetSpeedCommand(speedValue.value);
    const response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
    
    if (response.success) {
      // notification.success({ message: `设置速度成功: ${speedValue.value}m/s` });
      console.log(`✅ 设置速度成功: ${speedValue.value}m/s`);
      updateResponse('设置速度', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `设置速度失败: ${response.message}` });
      console.error(`❌ 设置速度失败: ${response.message}`);
      updateResponse('设置速度失败', response);
    }
  } catch (error) {
    // notification.error({ message: '设置速度失败' });
    console.error('💥 设置速度失败:', error);
    updateResponse('设置速度失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const startPatrol = async () => {
  if (!selectedDrone.value) return;
  
  commandLoading.value = true;
  try {
    const command = createPatrolCommand(
      patrolParams.trajectoryType as any,
      patrolParams.size,
      patrolParams.altitude,
      patrolParams.speed
    );
    
    const response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
    
    if (response.success) {
      // notification.success({ message: `开始${patrolParams.trajectoryType}轨迹巡航` });
      console.log(`✅ 开始${patrolParams.trajectoryType}轨迹巡航`);
      updateResponse('开始轨迹巡航', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `开始巡航失败: ${response.message}` });
      console.error(`❌ 开始巡航失败: ${response.message}`);
      updateResponse('开始巡航失败', response);
    }
  } catch (error) {
    // notification.error({ message: '开始巡航失败' });
    console.error('💥 开始巡航失败:', error);
    updateResponse('开始巡航失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const stopPatrol = async () => {
  if (!selectedDrone.value) return;
  
  commandLoading.value = true;
  try {
    const command = createDroneCommand('STOP_PATROL');
    const response = await sendDroneControlCommand(selectedDrone.value.droneId, command);
    
    if (response.success) {
      // notification.success({ message: '停止巡航成功' });
      console.log('✅ 停止巡航成功');
      updateResponse('停止巡航', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `停止巡航失败: ${response.message}` });
      console.error(`❌ 停止巡航失败: ${response.message}`);
      updateResponse('停止巡航失败', response);
    }
  } catch (error) {
    // notification.error({ message: '停止巡航失败' });
    console.error('💥 停止巡航失败:', error);
    updateResponse('停止巡航失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const sendRawCommand = async () => {
  if (!selectedDrone.value || !rawCommand.value.trim()) return;
  
  commandLoading.value = true;
  try {
    const commandObj = JSON.parse(rawCommand.value);
    const response = await apiSendRawCommand(selectedDrone.value.droneId, commandObj);
    
    if (response.success) {
      // notification.success({ message: '原始命令发送成功' });
      console.log('✅ 原始命令发送成功');
      updateResponse('原始命令', response);
      loadCommandHistory();
    } else {
      // notification.error({ message: `原始命令发送失败: ${response.message}` });
      console.error(`❌ 原始命令发送失败: ${response.message}`);
      updateResponse('原始命令失败', response);
    }
  } catch (error) {
    if (error instanceof SyntaxError) {
      // notification.error({ message: 'JSON 格式错误' });
      console.error('❌ JSON 格式错误:', error);
    } else {
      // notification.error({ message: '发送原始命令失败' });
      console.error('💥 发送原始命令失败:', error);
    }
    updateResponse('发送原始命令失败', error);
  } finally {
    commandLoading.value = false;
  }
};

const formatCommand = () => {
  try {
    const obj = JSON.parse(rawCommand.value);
    rawCommand.value = JSON.stringify(obj, null, 2);
    // notification.success({ message: '格式化成功' });
    console.log('✅ 格式化成功');
  } catch (error) {
    // notification.error({ message: 'JSON 格式错误' });
    console.error('❌ JSON 格式错误:', error);
  }
};

const loadCommandTemplate = () => {
  rawCommand.value = JSON.stringify({
    action: "GOTO",
    parameters: {
      latitude: 41.878113,
      longitude: 123.430201,
      altitude: 25,
      speed: 5
    },
    priority: 6,
    timeoutSeconds: 60
  }, null, 2);
};

const loadCommandHistory = async () => {
  if (!selectedDrone.value) return;
  
  historyLoading.value = true;
  try {
    console.log('开始加载命令历史');
    commandHistory.value = await getDroneCommandHistory(selectedDrone.value.droneId, 10);
    console.log(`成功加载 ${commandHistory.value.length} 条命令历史`);
    updateResponse('命令历史加载', `获取到 ${commandHistory.value.length} 条记录`);
  } catch (error) {
    console.error('加载命令历史失败:', error);
    // 不显示错误通知，因为这不是关键功能
    // notification.error({ message: '加载命令历史失败' });
    updateResponse('命令历史加载失败', error);
  } finally {
    historyLoading.value = false;
  }
};

const clearHistory = () => {
  commandHistory.value = [];
  updateResponse('命令历史', '显示已清空');
};

const cancelCommand = async (commandId: string) => {
  if (!selectedDrone.value) return;
  
  try {
    const result = await cancelDroneCommand(selectedDrone.value.droneId, commandId);
    if (result.success) {
      // notification.success({ message: '命令取消成功' });
      console.log('✅ 命令取消成功');
      loadCommandHistory();
    } else {
      // notification.error({ message: '命令取消失败' });
      console.error('❌ 命令取消失败');
    }
    updateResponse('取消命令', result);
  } catch (error) {
    // notification.error({ message: '取消命令失败' });
    console.error('💥 取消命令失败:', error);
    updateResponse('取消命令失败', error);
  }
};

const confirmEmergencyStop = () => {
  Modal.confirm({
    title: '确认紧急停止',
    content: '确定要对当前无人机执行紧急停止吗？此操作不可撤销！',
    okType: 'danger',
    onOk: () => sendQuickCommand('EMERGENCY_STOP')
  });
};

const confirmEmergencyStopAll = () => {
  Modal.confirm({
    title: '确认紧急停止所有无人机',
    content: '确定要对所有无人机执行紧急停止吗？此操作将影响系统中的所有无人机，不可撤销！',
    okType: 'danger',
    onOk: async () => {
      emergencyLoading.value = true;
      try {
        const response = await emergencyStopAll();
        if (response.success) {
          // notification.success({ message: `紧急停止成功，影响 ${response.affectedDrones.length} 架无人机` });
          console.log(`✅ 紧急停止成功，影响 ${response.affectedDrones.length} 架无人机`);
        } else {
          // notification.error({ message: '紧急停止失败' });
          console.error('❌ 紧急停止失败');
        }
        updateResponse('紧急停止所有无人机', response);
      } catch (error) {
        // notification.error({ message: '紧急停止所有无人机失败' });
        console.error('💥 紧急停止所有无人机失败:', error);
        updateResponse('紧急停止所有无人机失败', error);
      } finally {
        emergencyLoading.value = false;
      }
    }
  });
};

// 绘制地理围栏
const toggleGeofence = () => {
  if (!map.value || !selectedDrone.value) return;

  // 切换围栏状态
  geofenceActive.value = !geofenceActive.value;

  // 清除现有围栏
  map.value.clearOverlays();

  // 重新添加无人机标记
  renderDroneMarkers();

  // 如果开启围栏，绘制围栏圆圈
  if (geofenceActive.value) {
    const BMap = window.BMap;
    const drone = selectedDrone.value;
    const point = new BMap.Point(drone.position.longitude, drone.position.latitude);

    // 创建地理围栏圆形
    const circle = new BMap.Circle(point, geofenceRadius.value, {
      strokeColor: "#1890ff",
      strokeWeight: 2,
      strokeOpacity: 0.8,
      fillColor: "#1890ff",
      fillOpacity: 0.1
    });

    // 添加到地图
    map.value.addOverlay(circle);

    notification.info({
      message: '地理围栏已启用',
      description: `已为无人机 ${drone.serialNumber} 设置${geofenceRadius.value}米半径的地理围栏`,
    });
  } else {
    notification.info({
      message: '地理围栏已禁用',
      description: `已为无人机 ${selectedDrone.value.serialNumber} 禁用地理围栏`,
    });
  }
};

// 打开MQTT消息对话框
const openMqttModal = () => {
  if (!selectedDrone.value) return;

  mqttForm.topic = selectedDrone.value.mqtt.topicCommands;
  mqttForm.message = '';
  mqttModalVisible.value = true;
};

// 发送MQTT消息
const sendMqttMessage = () => {
  if (!selectedDrone.value || !mqttForm.message) {
    notification.warning({
      message: '发送失败',
      description: '请输入消息内容',
    });
    return;
  }

  // 模拟MQTT消息发送
  notification.success({
    message: 'MQTT消息已发送',
    description: `主题: ${mqttForm.topic}, 消息: ${mqttForm.message}`,
  });

  // 关闭对话框
  mqttModalVisible.value = false;
};

// 检查后端API是否可用
const checkBackendAvailability = async () => {
  try {
    const response = await axios.get(`${backendApiUrl.value}/api/test/generate-drones`, {
      params: { count: 1 },
      timeout: 3000 // 3秒超时
    });
    return response.status === 200;
  } catch (error) {
    console.error('后端API检测失败:', error);
    return false;
  }
};

// 检查未更新的无人机
const checkStaleData = () => {
  const now = new Date().toISOString();
  const currentTime = new Date(now).getTime();

  Object.keys(realDrones.value).forEach(droneId => {
    const drone = realDrones.value[droneId];
    if (drone && drone.lastHeartbeat) {
      const lastUpdateTime = new Date(drone.lastHeartbeat).getTime();
      const timeDiff = currentTime - lastUpdateTime;

      // 如果超过30秒没有收到更新，将无人机标记为离线
      if (timeDiff > 30000 && drone.status !== 'OFFLINE') {
        console.log(`将无人机 ${droneId} 标记为离线，最后心跳时间: ${drone.lastHeartbeat}`);
        drone.status = 'OFFLINE';
      }
    }
  });

  // 更新标记
    renderDroneMarkers();
  };

// 用于清理的变量
let staleCheckInterval: number | null = null;
let mapInitTimeout: number | null = null;

// 创建专用的脚本容器
function createScriptContainer() {
  // 如果已存在，先移除旧容器
  if (mapScriptContainer.value && mapScriptContainer.value.parentNode) {
    mapScriptContainer.value.parentNode.removeChild(mapScriptContainer.value);
  }

  // 创建新容器
  const container = document.createElement('div');
  container.id = 'baiduMapScriptContainer-' + Date.now();
  container.style.display = 'none';
  document.body.appendChild(container);
  mapScriptContainer.value = container;
  return container;
}

// 加载百度地图脚本
function loadBaiduMapScript() {
  if (mapScriptLoaded.value) return;

  // 创建专用脚本容器
  const container = createScriptContainer();

  // 创建脚本元素
  const script = document.createElement('script');
  script.type = 'text/javascript';
  script.src = `https://api.map.baidu.com/api?v=3.0&ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&callback=initMapInstance`;
  script.async = true;
  script.defer = true;

  // 添加到专用容器而非document.body
  container.appendChild(script);

  console.log('百度地图脚本已添加到容器', container.id);
  mapScriptLoaded.value = true;
}

// 组件卸载时清理
onUnmounted(() => {
  console.log('组件正在卸载...');

  // 标记组件已卸载
  isComponentMounted.value = false;

  // 关闭WebSocket连接
  closeWebSocket();

  // 清除检查过期数据的定时器
  if (staleCheckInterval !== null) {
    clearInterval(staleCheckInterval);
    staleCheckInterval = null;
  }

  // 清除地图初始化的定时器
  if (mapInitTimeout !== null) {
    clearTimeout(mapInitTimeout);
    mapInitTimeout = null;
  }

  // 移除可能存在的全局回调，避免地图API在组件卸载后执行回调
  if (window.initMapInstance) {
    window.initMapInstance = () => {
      console.log('地图回调已被取消，组件已卸载');
    };
  }

  // 清理地图实例
  if (map.value) {
    try {
      // 尝试清除地图实例
      // 注意：仅设为null，让GC处理，避免手动销毁可能引起的错误
      map.value = null;
    } catch (e) {
      console.error('清理地图实例时出错:', e);
    }
  }

  // 延迟移除脚本容器，等待可能的异步操作完成
  setTimeout(() => {
    try {
      // 移除脚本容器及其中的所有脚本
      if (mapScriptContainer.value && mapScriptContainer.value.parentNode) {
        mapScriptContainer.value.parentNode.removeChild(mapScriptContainer.value);
        mapScriptContainer.value = null;
      }
    } catch (e) {
      console.error('移除脚本容器时出错:', e);
    }
  }, 500);

  console.log('组件卸载完成');
});

// 生命周期钩子
onMounted(async () => {
  console.log('无人机状态监控组件已挂载');
  
  try {
    // 连接WebSocket
    initWebSocket();
    
    // 加载所有地理围栏数据，用于后续的无人机关联显示
    await loadAllGeofences();
    
    // 初始化地图（使用延迟，确保DOM已渲染）
    setTimeout(() => {
      initBaiduMap();
    }, 1000);
    
    // 5秒后检查数据状态
    setTimeout(() => {
      if (Object.keys(realDrones.value).length === 0) {
        notification.info({
          message: '等待无人机数据',
          description: '目前没有收到任何无人机数据。请确保：\n1. Python无人机模拟器正在运行\n2. 模拟器已被管理员批准注册\n3. WebSocket连接正常',
          duration: 8
        });
      }
    }, 5000);
    
    // 启动定期检查过期数据的定时器
    staleCheckInterval = setInterval(checkStaleData, 60000) as unknown as number; // 每60秒检查一次
  } catch (error) {
    console.error('组件初始化时出错:', error);
  }
});

// 初始化百度地图函数
const initBaiduMap = () => {
  console.log('初始化百度地图...');

  // 添加延迟确保DOM已加载
  mapInitTimeout = setTimeout(() => {
    // 确保组件仍然挂载
    if (!isComponentMounted.value) {
      console.log('组件已卸载，取消地图初始化');
      return;
    }

    // 检查并确保地图容器存在
    const mapContainer = document.getElementById('baiduMap');
    if (!mapContainer) {
      console.error('找不到地图容器: #baiduMap');
      return;
    }

    // 显式设置容器大小
    mapContainer.style.width = '100%';
    mapContainer.style.height = '700px';

    try {
      // 设置全局回调
      window.BMap_loadScriptTime = (new Date()).getTime();
      window.initMapInstance = () => {
        // 确保组件仍然挂载
        if (!isComponentMounted.value) {
          console.log('组件已卸载，取消地图实例初始化');
          return;
        }

        try {
          console.log('百度地图API加载完成');
          const BMap = window.BMap;

          if (!BMap) {
            console.error('BMap未定义');
            return;
          }

          // 先检查地图容器是否还存在
          const container = document.getElementById('baiduMap');
          if (!container) {
            console.error('地图容器已消失');
            return;
          }

          // 创建地图
          console.log('创建地图实例...');
          const bmap = new BMap.Map(container);
          // 沈阳市中心坐标（或其他合适的默认位置）
          const point = new BMap.Point(123.4315, 41.8057);
          bmap.centerAndZoom(point, 12);

          // 添加控件
          bmap.addControl(new BMap.NavigationControl());
          bmap.addControl(new BMap.ScaleControl());
          bmap.enableScrollWheelZoom(true);

          // 保存地图实例
          map.value = bmap;
          console.log('地图实例创建成功!');

          // 渲染无人机标记
          if (isComponentMounted.value) {
            renderDroneMarkers();
          }
        } catch (e) {
          console.error('地图初始化失败:', e);
        }
      };

      // 加载地图脚本
      loadBaiduMapScript();
    } catch (e) {
      console.error('加载地图API失败:', e);
    }
  }, 1000) as unknown as number;
};

// 显示下线确认对话框
const showOfflineModal = () => {
  if (!selectedDrone.value) return;

  // 检查无人机状态，只有地面待命的无人机才能下线
  if (selectedDrone.value.status !== 'IDLE') {
    notification.warning({
      message: '无法下线',
      description: '只有处于地面待命状态的无人机才能被下线'
    });
    return;
  }

  offlineReason.value = '';
  offlineDroneId.value = selectedDrone.value.droneId;
  offlineModalVisible.value = true;
};

// 处理下线无人机
const handleOfflineDrone = async () => {
  // 基本验证
  if (!offlineReason.value.trim()) {
    notification.error({
      message: '缺少必要信息',
      description: '请输入下线原因'
    });
    return;
  }

  if (offlineReason.value.trim().length < 5) {
    notification.error({
      message: '下线原因太短',
      description: '请提供至少5个字符的下线原因'
    });
    return;
  }

  processingOffline.value = true;

  try {
    const response = await fetch(`${backendApiUrl.value}/api/v1/drones/management/offline`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 添加Token（如果有）
        ...(localStorage.getItem('token') ?
          { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
      },
      body: JSON.stringify({
        droneId: offlineDroneId.value,
        reason: offlineReason.value,
        gracePeriodSeconds: 10
      })
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      throw new Error(errorData?.message || `操作失败: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();

    if (data.success) {
      notification.success({
        message: '无人机下线成功',
        description: data.message
      });

      // 关闭对话框
      offlineModalVisible.value = false;

      // 如果是当前选中的无人机，更新状态显示
      if (selectedDrone.value && selectedDrone.value.droneId === offlineDroneId.value) {
        selectedDrone.value.status = 'OFFLINE';
      }

      // 关闭抽屉，等待WebSocket更新无人机状态
      drawerVisible.value = false;
    } else {
      notification.error({
        message: '无人机下线失败',
        description: data.message
      });
    }
  } catch (error) {
    console.error('下线无人机出错:', error);
    notification.error({
      message: '无人机下线失败',
      description: typeof error === 'object' && error !== null && 'message' in error ?
        (error as Error).message : '操作过程中发生错误'
    });
  } finally {
    processingOffline.value = false;
  }
};

// Add this function with the other UI functions
const closeDrawer = () => {
  drawerVisible.value = false;
};

// 处理特定无人机的状态更新
const handleDroneStatusUpdate = (update: any) => {
  try {
    console.log(`收到无人机${update.droneId}状态更新:`, update);

    // 确保realDrones中存在此无人机
    if (!realDrones.value[update.droneId]) {
      console.log(`创建无人机${update.droneId}的条目`);
      // 创建新的无人机记录
      realDrones.value[update.droneId] = {
        droneId: update.droneId,
        serialNumber: update.serialNumber || `无人机-${update.droneId.substring(0, 8)}`,
        model: update.model || '未知型号',
        status: update.status || 'OFFLINE',
        batteryPercentage: update.batteryLevel || 0,
        position: {
          latitude: update.latitude || 0,
          longitude: update.longitude || 0,
          altitude: update.altitude || 0
        },
        speed: update.speed || 0,
        lastHeartbeat: update.lastHeartbeat || new Date().toISOString(),
        mqtt: {
          username: update.mqttUsername || '',
          topicTelemetry: update.mqttTopicTelemetry || '',
          topicCommands: update.mqttTopicCommands || ''
        },
        flightMode: update.flightMode || 'UNKNOWN',
        // 离线相关信息
        offlineAt: update.offlineAt,
        offlineReason: update.offlineReason,
        offlineBy: update.offlineBy,
        lastFarewellMessage: update.lastFarewellMessage
      };
    } else {
      // 更新现有无人机
      const drone = realDrones.value[update.droneId];

      if (drone) {
        // 更新状态
        if (update.status) {
          drone.status = update.status;
        }

        // 更新位置信息
        if (update.latitude && update.longitude) {
          drone.position.latitude = update.latitude;
          drone.position.longitude = update.longitude;
        }

        if (update.altitude) {
          drone.position.altitude = update.altitude;
        }

        // 更新电池电量
        if (update.batteryLevel !== undefined) {
          drone.batteryPercentage = update.batteryLevel;
        }

        // 更新速度
        if (update.speed !== undefined) {
          drone.speed = update.speed;
        }

        // 更新心跳时间
        if (update.lastHeartbeat) {
          drone.lastHeartbeat = update.lastHeartbeat;
        }

        // 更新飞行模式
        if (update.flightMode) {
          drone.flightMode = update.flightMode;
        }

        // 更新离线信息
        if (update.status === 'OFFLINE') {
          if (update.offlineAt) drone.offlineAt = update.offlineAt;
          if (update.offlineReason) drone.offlineReason = update.offlineReason;
          if (update.offlineBy) drone.offlineBy = update.offlineBy;
          if (update.lastFarewellMessage) drone.lastFarewellMessage = update.lastFarewellMessage;
        }
      }
    }

    // 更新选定无人机，如果当前选择的是这个无人机
    if (selectedDrone.value && selectedDrone.value.droneId === update.droneId) {
      // 创建一个副本而不是直接引用，以确保视图更新
      selectedDrone.value = { ...realDrones.value[update.droneId] } as DroneData;
    }

    // 在实际应用中，可能需要重新渲染地图标记
    renderDroneMarkers();
  } catch (error) {
    console.error('处理无人机状态更新时出错:', error);
  }
};

// 处理无人机删除事件
const handleDroneDeleted = (droneId: string) => {
  console.log(`处理无人机删除: ${droneId}`);

  // 如果当前选中的是被删除的无人机，关闭抽屉
  if (selectedDrone.value && selectedDrone.value.droneId === droneId) {
    drawerVisible.value = false;
    selectedDrone.value = null;
  }

  // 从realDrones集合中移除该无人机
  if (realDrones.value[droneId]) {
    console.log(`从监控列表中移除无人机: ${droneId}`);
    delete realDrones.value[droneId];

    // 更新标记
    renderDroneMarkers();

    // 显示通知
    notification.info({
      message: '无人机已从系统中删除',
      description: `无人机ID: ${droneId} 已从系统中移除`,
      duration: 3
    });
  }
};

// 添加函数：聚焦到特定无人机
const centerMapOnDrone = (drone: DroneData) => {
  if (!map.value || !drone || !drone.position) return;

  const BMap = window.BMap;
  const center = new BMap.Point(drone.position.longitude, drone.position.latitude);
  map.value.centerAndZoom(center, 14); // 放大级别14，可以根据需要调整
};

// 添加函数：聚焦地图以显示所有无人机
const focusAllDrones = () => {
  // 首先检查基本条件
  if (!map.value) {
    console.error('地图实例不存在');
    notification.error({
      message: '操作失败',
      description: '地图尚未初始化'
    });
    return;
  }

  if (activeDrones.value.length === 0) {
    notification.info({
      message: '无人机数据为空',
      description: '当前没有可用的无人机数据'
    });
    return;
  }

  // 使用延迟执行确保地图已完全加载
  setTimeout(() => {
    try {
      // 确保BMap存在
      if (typeof window.BMap === 'undefined') {
        console.error('BMap未定义');
        notification.error({
          message: '地图API错误',
          description: '百度地图API未正确加载'
        });
        return;
      }

      // 单无人机情况
      if (activeDrones.value.length === 1) {
        const drone = activeDrones.value[0];
        if (drone && drone.position) {
          try {
            const point = new window.BMap.Point(drone.position.longitude, drone.position.latitude);
            map.value.centerAndZoom(point, 14);
            console.log('已聚焦到单架无人机');

            notification.success({
              message: '聚焦成功',
              description: `已聚焦到无人机 ${drone.serialNumber}`,
              duration: 2
            });
          } catch (e) {
            console.error('设置单架无人机聚焦失败:', e);
            notification.error({
              message: '操作失败',
              description: '无法聚焦到无人机位置'
            });
          }
        }
        return;
      }

      // 多无人机情况 - 使用视口设置
      try {
        // 创建所有点的范围
        const points: any[] = [];

        // 收集有效的点
        activeDrones.value.forEach(drone => {
          if (drone && drone.position) {
            points.push(new window.BMap.Point(drone.position.longitude, drone.position.latitude));
          }
        });

        if (points.length > 0) {
          // 使用视口方法设置地图，而不是bounds
          map.value.setViewport(points);

          console.log(`已聚焦地图以显示${points.length}架无人机`);
          notification.success({
            message: '聚焦成功',
            description: `已显示全部${points.length}架无人机`,
            duration: 2
          });
        } else {
          throw new Error('没有有效的坐标点');
        }
      } catch (e) {
        console.error('设置多架无人机视图失败:', e);
        notification.error({
          message: '操作失败',
          description: '无法聚焦到所有无人机位置'
        });
      }
    } catch (e) {
      console.error('执行自动聚焦时发生错误:', e);
    }
  }, 100); // 短暂延迟以确保地图已完全初始化
};

// ============================================================================
// 地理围栏相关功能
// ============================================================================

// 地理围栏样式配置
const geofenceStyles = {
  NO_FLY_ZONE: {
    strokeColor: '#ff4d4f',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#ff4d4f',
    fillOpacity: 0.2
  },
  FLY_ZONE: {
    strokeColor: '#52c41a',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#52c41a',
    fillOpacity: 0.1
  },
  RESTRICTED_ZONE: {
    strokeColor: '#faad14',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#faad14',
    fillOpacity: 0.2
  }
};

// 地理围栏相关状态
const droneGeofences = ref<GeofenceListItem[]>([]);
const availableGeofences = ref<GeofenceListItem[]>([]);
const selectedGeofences = ref<Set<string>>(new Set());
// 添加所有地理围栏状态
const allGeofences = ref<GeofenceData[]>([]);

// 加载状态
const loadingDroneGeofences = ref(false);
const loadingAvailableGeofences = ref(false);
const assigningGeofences = ref(false);
const removingGeofences = ref<Set<string>>(new Set());
// 添加加载所有地理围栏的状态
const loadingAllGeofences = ref(false);

// 筛选选项
const geofenceTypeFilter = ref<string>();
const showActiveOnly = ref(true);

// 计算属性：过滤后的可用地理围栏
const filteredAvailableGeofences = computed(() => {
  return availableGeofences.value.filter(geofence => {
    // 排除已分配的地理围栏
    const isAssigned = droneGeofences.value.some(assigned =>
      assigned.geofenceId === geofence.geofenceId
    );
    return !isAssigned;
  });
});

// 加载无人机关联的地理围栏
const loadDroneGeofences = async () => {
  if (!selectedDrone.value?.droneId) return;

  loadingDroneGeofences.value = true;
  try {
    droneGeofences.value = await getDroneGeofences(selectedDrone.value.droneId);
    console.log(`加载到${droneGeofences.value.length}个已分配的地理围栏`);
  } catch (error) {
    // 这里的问题：API成功返回数据，但被当作错误处理了
    console.error('加载无人机地理围栏失败:', error);
    notification.error({
      message: '加载失败',
      description: '无法获取无人机地理围栏信息'
    });
  } finally {
    loadingDroneGeofences.value = false;
  }
};

// 加载可用的地理围栏
const loadAvailableGeofences = async () => {
  if (!selectedDrone.value?.droneId) return;

  loadingAvailableGeofences.value = true;
  try {
    availableGeofences.value = await getAvailableGeofences(
      selectedDrone.value.droneId,
      geofenceTypeFilter.value,
      showActiveOnly.value
    );
    console.log(`加载到${availableGeofences.value.length}个可用地理围栏`);
  } catch (error) {
    // 这里的问题：API成功返回数据，但被当作错误处理了
    console.error('加载可用地理围栏失败:', error);
    notification.error({
      message: '加载失败',
      description: '无法获取可用地理围栏信息'
    });
  } finally {
    loadingAvailableGeofences.value = false;
  }
};

// 加载所有地理围栏（参照地理围栏页面的实现）
const loadAllGeofences = async () => {
  loadingAllGeofences.value = true;
  try {
    allGeofences.value = await getAllGeofences();
    console.log(`加载到${allGeofences.value.length}个地理围栏`);

    notification.success({
      message: '加载成功',
      description: `成功加载 ${allGeofences.value.length} 个地理围栏`,
    });
  } catch (error: any) {
    console.error('加载地理围栏失败:', error);

    // 设置为空数组，避免页面崩溃
    allGeofences.value = [];

    // 根据错误类型显示不同的提示
    if (error.response?.status === 500) {
      notification.warning({
        message: '服务器错误',
        description: '地理围栏服务暂时不可用，显示空列表。这可能是因为数据库中还没有地理围栏数据。',
      });
    } else if (error.response?.status === 403) {
      notification.error({
        message: '权限不足',
        description: '没有权限访问地理围栏数据，请联系管理员',
      });
    } else {
      notification.error({
        message: '加载失败',
        description: '无法从服务器获取地理围栏数据，请检查网络连接或稍后重试',
      });
    }
  } finally {
    loadingAllGeofences.value = false;
  }
};

// 移除地理围栏
const removeGeofence = async (geofenceId: string) => {
  if (!selectedDrone.value?.droneId) return;

  removingGeofences.value.add(geofenceId);
  try {
    const response = await unassignGeofence(selectedDrone.value.droneId, geofenceId);

    if (response.success) {
      notification.success({
        message: '移除成功',
        description: response.message
      });
      // 重新加载数据
      await loadDroneGeofences();
      await loadAvailableGeofences();
    } else {
      notification.error({
        message: '移除失败',
        description: response.message
      });
    }
  } catch (error) {
    console.error('移除地理围栏失败:', error);
    notification.error({
      message: '移除失败',
      description: '无法移除地理围栏权限'
    });
  } finally {
    removingGeofences.value.delete(geofenceId);
  }
};

// 切换地理围栏选择状态
const toggleGeofenceSelection = (geofenceId: string) => {
  if (selectedGeofences.value.has(geofenceId)) {
    selectedGeofences.value.delete(geofenceId);
  } else {
    selectedGeofences.value.add(geofenceId);
  }
  // 触发响应式更新
  selectedGeofences.value = new Set(selectedGeofences.value);
};

// 清除选择
const clearGeofenceSelection = () => {
  selectedGeofences.value.clear();
  selectedGeofences.value = new Set();
};

// 分配选中的地理围栏
const assignSelectedGeofences = async () => {
  if (!selectedDrone.value?.droneId || selectedGeofences.value.size === 0) return;

  assigningGeofences.value = true;
  try {
    const geofenceIds = Array.from(selectedGeofences.value);
    const response = await assignGeofences(selectedDrone.value.droneId, geofenceIds);

    if (response.success) {
      notification.success({
        message: '分配成功',
        description: response.message
      });

      // 清除选择并重新加载数据
      clearGeofenceSelection();
      await loadDroneGeofences();
      await loadAvailableGeofences();
    } else {
      notification.error({
        message: '分配失败',
        description: response.message
      });
    }
  } catch (error) {
    console.error('分配地理围栏失败:', error);
    notification.error({
      message: '分配失败',
      description: '无法分配地理围栏权限'
    });
  } finally {
    assigningGeofences.value = false;
  }
};

// 从所有地理围栏列表中分配地理围栏给无人机
const assignGeofenceFromAll = async (geofenceId: string) => {
  if (!selectedDrone.value?.droneId) return;

  try {
    const response = await assignGeofences(selectedDrone.value.droneId, [geofenceId]);

    if (response.success) {
      notification.success({
        message: '分配成功',
        description: response.message
      });

      // 重新加载数据
      await Promise.all([
        loadDroneGeofences(),
        loadAvailableGeofences()
      ]);
    } else {
      notification.error({
        message: '分配失败',
        description: response.message
      });
    }
  } catch (error) {
    console.error('分配地理围栏失败:', error);
    notification.error({
      message: '分配失败',
      description: '无法分配地理围栏权限'
    });
  }
};

// 获取地理围栏类型颜色
const getGeofenceTypeColor = (type: string): string => {
  const colorMap: Record<string, string> = {
    'NO_FLY_ZONE': 'red',
    'FLY_ZONE': 'green',
    'RESTRICTED_ZONE': 'orange',
  };
  return colorMap[type] || 'default';
};

// 获取地理围栏类型文本
const getGeofenceTypeText = (type: string): string => {
  const textMap: Record<string, string> = {
    'NO_FLY_ZONE': '禁飞区',
    'FLY_ZONE': '允飞区',
    'RESTRICTED_ZONE': '限制区',
  };
  return textMap[type] || type;
};

// 格式化面积
const formatArea = (areaSquareMeters: number): string => {
  if (areaSquareMeters < 1000) {
    return `${areaSquareMeters.toFixed(0)}㎡`;
  } else if (areaSquareMeters < 1000000) {
    return `${(areaSquareMeters / 1000).toFixed(1)}k㎡`;
  } else {
    return `${(areaSquareMeters / 1000000).toFixed(2)}k㎡`;
  }
};

// 检查地理围栏是否已经分配给当前无人机
const isGeofenceAssigned = (geofenceId: string): boolean => {
  return droneGeofences.value.some(assigned => assigned.geofenceId === geofenceId);
};

// 监听选中无人机变化，自动加载地理围栏数据
watch(selectedDrone, async (newDrone) => {
  if (newDrone) {
    await Promise.all([
      loadDroneGeofences(),
      loadAvailableGeofences()
    ]);
    
    // 当选中新的无人机时，自动更新地理围栏显示
    if (showDroneGeofences.value) {
      updateDroneGeofenceDisplay();
    }
  }
});

// 监听地理围栏显示开关变化
watch(showDroneGeofences, (enabled) => {
  if (enabled && selectedDrone.value) {
    // 开启时显示地理围栏
    updateDroneGeofenceDisplay();
  } else {
    // 关闭时清除地理围栏显示
    clearGeofenceOverlays();
  }
});

// 监听抽屉打开状态，当打开地理围栏标签时加载数据
watch(activeTabKey, async (newKey) => {
  if (newKey === '3' && selectedDrone.value?.droneId) {
    await Promise.all([
      loadDroneGeofences(),
      loadAvailableGeofences(),
      loadAllGeofences() // 添加加载所有地理围栏
    ]);
  }
});

// 扩展Window接口以包含BMap_loadScriptTime属性
declare global {
  interface Window {
    BMap: any;
    BMap_Symbol_SHAPE_POINT: any;
    BMap_Symbol_SHAPE_PLANE: any;
    BMap_Symbol_SHAPE_WARNING: any;
    initMapInstance: () => void;
    BMap_loadScriptTime: number;
  }
}

const geofenceTypeNames = {
  'NO_FLY_ZONE': '禁飞区',
  'FLY_ZONE': '允飞区',
  'RESTRICTED_ZONE': '限制区',
};

// 清除地图上的地理围栏显示
const clearGeofenceOverlays = () => {
  if (!map.value) return;
  
  geofenceOverlays.value.forEach(overlay => {
    map.value.removeOverlay(overlay);
  });
  geofenceOverlays.value = [];
};

// 在地图上渲染地理围栏
const renderGeofencesOnMap = (geofences: GeofenceData[]) => {
  if (!map.value || !window.BMap) return;
  
  const BMap = window.BMap;
  
  // 清除现有地理围栏
  clearGeofenceOverlays();
  
  console.log(`准备在地图上渲染${geofences.length}个地理围栏`);
  
  geofences.forEach(geofence => {
    try {
      // 将坐标转换为百度地图点
      const points = geofence.coordinates.map(coord => 
        new BMap.Point(coord.lng, coord.lat)
      );
      
      if (points.length < 3) {
        console.warn(`地理围栏 ${geofence.name} 坐标点少于3个，跳过渲染`);
        return;
      }
      
      // 获取样式配置
      const style = geofenceStyles[geofence.type] || geofenceStyles.RESTRICTED_ZONE;
      
      // 创建多边形
      const polygon = new BMap.Polygon(points, style);
      
      // 添加信息窗口
      const infoWindow = new BMap.InfoWindow(`
        <div style="width: 200px; padding: 8px; font-family: Arial, sans-serif;">
          <div style="font-weight: bold; color: ${style.strokeColor}; margin-bottom: 8px; border-bottom: 1px solid #eee; padding-bottom: 4px;">
            ${geofence.name}
          </div>
          <div style="font-size: 12px; line-height: 1.6; color: #333;">
            <div><strong>类型:</strong> ${getGeofenceTypeText(geofence.type)}</div>
            <div><strong>状态:</strong> ${geofence.active ? '活跃' : '非活跃'}</div>
            ${geofence.description ? `<div><strong>描述:</strong> ${geofence.description}</div>` : ''}
            <div style="margin-top: 8px; font-size: 11px; color: #666;">
              点击查看详细信息
            </div>
          </div>
        </div>
      `, {
        enableCloseOnClick: true,
        width: 0,
        height: 0
      });
      
      // 添加点击事件
      polygon.addEventListener('click', () => {
        polygon.openInfoWindow(infoWindow);
      });
      
      // 添加到地图
      map.value.addOverlay(polygon);
      geofenceOverlays.value.push(polygon);
      
      console.log(`已渲染地理围栏: ${geofence.name} (${geofence.type})`);
    } catch (error) {
      console.error(`渲染地理围栏 ${geofence.name} 时出错:`, error);
    }
  });
  
  console.log(`成功渲染了${geofenceOverlays.value.length}个地理围栏`);
};

// 更新选中无人机的地理围栏显示
const updateDroneGeofenceDisplay = async () => {
  if (!selectedDrone.value || !showDroneGeofences.value) {
    clearGeofenceOverlays();
    return;
  }
  
  try {
    console.log(`加载无人机 ${selectedDrone.value.serialNumber} 的关联地理围栏`);
    
    // 获取无人机关联的地理围栏
    const droneGeofenceList = await getDroneGeofences(selectedDrone.value.droneId);
    
    // 从所有地理围栏中筛选出该无人机关联的限制区
    const associatedRestrictedZones = allGeofences.value.filter(geofence => 
      geofence.type === 'RESTRICTED_ZONE' && 
      droneGeofenceList.some(item => item.geofenceId === geofence.id)
    );
    
    // 获取所有对全体无人机生效的禁飞区和允许飞行区
    const globalZones = allGeofences.value.filter(geofence => 
      geofence.type === 'NO_FLY_ZONE' || geofence.type === 'FLY_ZONE'
    );
    
    // 合并所有需要显示的地理围栏
    const allZonesToDisplay = [...associatedRestrictedZones, ...globalZones];
    
    if (allZonesToDisplay.length > 0) {
      console.log(`找到${associatedRestrictedZones.length}个关联的限制区和${globalZones.length}个全局区域，开始渲染`);
      renderGeofencesOnMap(allZonesToDisplay);
      
      const restrictedCount = associatedRestrictedZones.length;
      const noFlyCount = globalZones.filter(z => z.type === 'NO_FLY_ZONE').length;
      const flyCount = globalZones.filter(z => z.type === 'FLY_ZONE').length;
      
      notification.info({
        message: '地理围栏已显示',
        description: `已显示无人机 ${selectedDrone.value.serialNumber} 的飞行区域：
          • ${restrictedCount} 个关联限制区
          • ${noFlyCount} 个禁飞区
          • ${flyCount} 个允许飞行区`,
        duration: 4
      });
    } else {
      console.log('该无人机没有关联的限制区，且当前没有全局区域');
      clearGeofenceOverlays();
      
      notification.info({
        message: '无地理围栏',
        description: `无人机 ${selectedDrone.value.serialNumber} 没有关联的限制区，且当前没有全局禁飞区或允许飞行区`,
        duration: 3
      });
    }
    
  } catch (error) {
    console.error('加载无人机地理围栏显示失败:', error);
    clearGeofenceOverlays();
  }
};

const onGeofenceDisplayToggle = () => {
  updateDroneGeofenceDisplay();
};
</script>

<template>
  <div class="p-5">
    <Card title="无人机状态监控" :loading="loading" class="shadow-md" :bodyStyle="{ padding: 0 }">
      <!-- 测试工具栏 -->
      <div v-if="false" class="absolute top-16 left-8 bg-white p-3 shadow-md rounded-md z-10">
        <h4 class="text-base font-medium mb-2 flex items-center">
          <ExperimentOutlined class="mr-1" />
          测试工具
        </h4>
        <div class="space-y-3">
          <div class="flex items-center">
            <span class="mr-2 w-24">后端API:</span>
            <Input v-model:value="backendApiUrl" placeholder="后端API地址" style="width: 200px" />
          </div>
          <div class="flex items-center">
            <span class="mr-2 w-24">无人机数量:</span>
            <Input v-model:value="droneCount" type="number" style="width: 80px" />
          </div>
          <div class="flex items-center">
            <span class="mr-2 w-24">更新间隔(ms):</span>
            <Input v-model:value="simulationInterval" type="number" style="width: 80px" />
          </div>
          <div class="flex space-x-2">
            <Button type="primary" @click="generateDroneData">
              生成一次数据
            </Button>
            <Button
              type="primary"
              :danger="simulationActive"
              @click="startDroneSimulation"
              :disabled="simulationActive"
            >
              启动持续模拟
            </Button>
          </div>
          <div class="text-xs text-gray-500">
            <p>WebSocket状态:
              <Tag :color="connected ? 'green' : 'red'">
                {{ connected ? '已连接' : '未连接' }}
              </Tag>
            </p>
            <p>显示无人机: {{ Object.keys(realDrones).length || mockDrones.length }}</p>
          </div>
        </div>
      </div>

      <!-- Map container without the button inside -->
      <div id="baiduMap" style="width: 100%; height: 700px; position: relative;"></div>

      <!-- Auto-focus button placed outside the map container -->
      <div class="absolute bottom-8 right-8 z-20 shadow-lg">
        <Button type="primary" @click="focusAllDrones" title="显示所有无人机" class="flex items-center">
          <template #icon><EnvironmentOutlined /></template>
          自动聚焦全部无人机
        </Button>
      </div>

      <!-- 状态图例 -->
      <div class="absolute top-16 right-8 bg-white p-3 shadow-md rounded-md z-10">
        <h4 class="text-base font-medium mb-2">无人机状态</h4>
        <div class="flex flex-col space-y-2">
          <div class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #1890ff;"></div>
            <span>飞行中</span>
          </div>
          <div class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #52c41a;"></div>
            <span>地面待命</span>
          </div>
          <div class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #faad14;"></div>
            <span>低电量警告</span>
          </div>
          <div class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #ff4d4f;"></div>
            <span>轨迹异常警告</span>
          </div>
          <div class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #ff4d4f;"></div>
            <span>禁飞区违规</span>
          </div>
          <div class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #d9d9d9;"></div>
            <span>离线</span>
          </div>
        </div>
        
        <!-- 地理围栏显示控制 -->
        <div class="mt-4 pt-3 border-t border-gray-200">
          <h4 class="text-sm font-medium mb-2">地理围栏显示</h4>
          <div class="flex items-center justify-between">
            <span class="text-sm">显示飞行区域</span>
            <Switch
              v-model:checked="showDroneGeofences"
              @change="onGeofenceDisplayToggle"
              size="small"
            />
          </div>
          <div class="text-xs text-gray-500 mt-1">
            显示选中无人机的关联限制区<br/>
            以及所有禁飞区和允许飞行区
          </div>
        </div>
      </div>
    </Card>

    <!-- 无人机详情抽屉 -->
    <Drawer
      title="无人机详情"
      placement="right"
      :width="600"
      :open="drawerVisible"
      :closable="true"
      @close="closeDrawer"
    >
      <template #extra>
        <Button type="default" @click="closeDrawer">
          关闭
        </Button>
      </template>
      <template #title>
        <div class="flex items-center justify-between" style="width: 100%;">
          <span>无人机 {{ selectedDrone?.serialNumber }}</span>
          <Space>
            <Button
              type="primary"
              size="small"
              @click="selectedDrone && centerMapOnDrone(selectedDrone)"
              :disabled="!selectedDrone"
            >
              <template #icon><EnvironmentOutlined /></template>
              地图聚焦
            </Button>
            <Button v-if="selectedDrone?.status !== 'OFFLINE'" type="primary" danger size="small" @click="showOfflineModal">
              下线
            </Button>
          </Space>
        </div>
      </template>
      <template v-if="selectedDrone">
        <!-- 标签页 -->
        <Tabs v-model:activeKey="activeTabKey">
          <!-- 基本信息标签 -->
          <Tabs.TabPane key="1" tab="基本信息">
            <Descriptions bordered :column="1">
              <Descriptions.Item label="无人机ID">{{ selectedDrone.droneId }}</Descriptions.Item>
              <Descriptions.Item label="序列号">{{ selectedDrone.serialNumber }}</Descriptions.Item>
              <Descriptions.Item label="型号">{{ selectedDrone.model }}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag :color="getStatusTag(selectedDrone.status).color">
                  {{ getStatusTag(selectedDrone.status).text }}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="电量">
                <div class="flex items-center">
                  <BarsOutlined />
                  <div class="ml-2 w-32 h-4 bg-gray-200 rounded-full overflow-hidden">
                    <div
                      class="h-full rounded-full"
                      :style="{
                        width: `${selectedDrone.batteryPercentage}%`,
                        backgroundColor: getBatteryColor(selectedDrone.batteryPercentage)
                      }"
                    ></div>
                  </div>
                  <span class="ml-2">{{ selectedDrone.batteryPercentage }}%</span>
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="位置">
                <div class="flex items-center">
                  <EnvironmentOutlined />
                  <span class="ml-2">
                    {{ selectedDrone.position.latitude.toFixed(6) }},
                    {{ selectedDrone.position.longitude.toFixed(6) }}
                  </span>
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="高度">{{ selectedDrone.position.altitude }}米</Descriptions.Item>
              <Descriptions.Item label="速度">{{ selectedDrone.speed }}m/s</Descriptions.Item>
              <Descriptions.Item label="最后心跳">
                <div class="flex items-center">
                  <ClockCircleOutlined />
                  <span class="ml-2">{{ new Date(selectedDrone.lastHeartbeat).toLocaleString() }}</span>
                </div>
              </Descriptions.Item>
            </Descriptions>
          </Tabs.TabPane>

          <!-- 控制指令标签 -->
          <Tabs.TabPane key="2" tab="控制指令">
            <div class="space-y-6">
              <!-- 系统状态 -->
              <Card title="系统状态" size="small" class="bg-gray-50">
                <div class="grid grid-cols-2 gap-4">
                  <div class="flex items-center justify-between">
                    <span>后端连接</span>
                    <Tag :color="connected ? 'green' : 'red'">
                      {{ connected ? '已连接' : '未连接' }}
                    </Tag>
                  </div>
                  <div class="flex items-center justify-between">
                    <span>无人机可用性</span>
                    <div class="flex items-center space-x-2">
                      <Tag v-if="availability" :color="availability.available ? 'green' : 'red'">
                        {{ availability.available ? '可用' : '不可用' }}
                      </Tag>
                      <Button size="small" @click="checkAvailability" :loading="availabilityLoading">
                        检查
                      </Button>
                    </div>
                  </div>
                </div>
              </Card>

              <!-- 无人机信息卡片 -->
              <Card title="无人机信息" size="small">
                <div class="grid grid-cols-2 gap-4 text-sm">
                  <div><strong>序列号:</strong> {{ selectedDrone?.serialNumber }}</div>
                  <div><strong>型号:</strong> {{ selectedDrone?.model }}</div>
                  <div><strong>状态:</strong> 
                    <Tag :color="getDroneStatusColor(selectedDrone?.status || '')">
                      {{ selectedDrone?.status }}
                    </Tag>
                  </div>
                  <div><strong>电量:</strong> {{ selectedDrone?.batteryPercentage }}%</div>
                  <div><strong>位置:</strong> {{ formatPosition(selectedDrone?.position) }}</div>
                  <div><strong>最后心跳:</strong> {{ selectedDrone?.lastHeartbeat ? formatTime(selectedDrone.lastHeartbeat) : '无' }}</div>
                </div>
              </Card>

              <!-- 快速命令 -->
              <Card title="快速命令" size="small">
                <div class="grid grid-cols-2 gap-3">
                  <Button 
                    type="primary" 
                    @click="sendQuickCommand('ARM')"
                    :disabled="!canSendCommand"
                    :loading="commandLoading"
                    class="h-10"
                  >
                    解锁 (ARM)
                  </Button>
                  <Button 
                    @click="sendQuickCommand('DISARM')"
                    :disabled="!canSendCommand"
                    :loading="commandLoading"
                    class="h-10"
                  >
                    锁定 (DISARM)
                  </Button>
                  <Button 
                    type="primary"
                    @click="showTakeoffDialog"
                    :disabled="!canSendCommand"
                    class="h-10"
                  >
                    起飞
                  </Button>
                  <Button 
                    @click="sendQuickCommand('RETURN_TO_HOME')"
                    :disabled="!canSendCommand"
                    :loading="commandLoading"
                    class="h-10"
                  >
                    返航
                  </Button>
                  <Button 
                    @click="sendQuickCommand('LAND')"
                    :disabled="!canSendCommand"
                    :loading="commandLoading"
                    class="h-10"
                  >
                    降落
                  </Button>
                  <Button 
                    @click="sendQuickCommand('HOVER')"
                    :disabled="!canSendCommand"
                    :loading="commandLoading"
                    class="h-10"
                  >
                    悬停
                  </Button>
                </div>
                
                <!-- 紧急操作 -->
                <div class="mt-4 pt-4 border-t border-gray-200">
                  <div class="grid grid-cols-2 gap-3">
                    <Button 
                      danger
                      @click="confirmEmergencyStop"
                      :disabled="!selectedDrone"
                      :loading="commandLoading"
                      class="h-10"
                    >
                      <template #icon><WarningOutlined /></template>
                      紧急停止
                    </Button>
                    <Button 
                      danger
                      @click="confirmEmergencyStopAll"
                      :loading="emergencyLoading"
                      class="h-10"
                    >
                      <template #icon><WarningOutlined /></template>
                      全部紧急停止
                    </Button>
                  </div>
                </div>
              </Card>

              <!-- 高级控制 -->
              <Card title="高级控制" size="small">
                <Tabs v-model:activeKey="controlActiveTab" size="small">
                  <!-- 运动控制 -->
                  <Tabs.TabPane key="movement" tab="运动控制">
            <div class="space-y-4">
                      <!-- GOTO命令 -->
                      <div class="p-4 border rounded-lg bg-gray-50">
                        <h4 class="font-medium mb-3">移动到指定位置 (GOTO)</h4>
                        <div class="grid grid-cols-2 gap-3 mb-3">
              <div>
                            <label class="block text-sm font-medium mb-1">纬度</label>
                            <Input 
                              v-model:value="latitudeDisplay" 
                              type="number" 
                              placeholder="如: 41.878113"
                              :step="0.000001"
                            />
                          </div>
                          <div>
                            <label class="block text-sm font-medium mb-1">经度</label>
                            <Input 
                              v-model:value="longitudeDisplay" 
                              type="number" 
                              placeholder="如: 123.430201"
                              :step="0.000001"
                            />
                          </div>
                          <div>
                            <label class="block text-sm font-medium mb-1">高度 (米)</label>
                            <Input v-model:value="gotoParams.altitude" type="number" :min="1" :max="500" />
                          </div>
                          <div>
                            <label class="block text-sm font-medium mb-1">速度 (m/s)</label>
                            <Input v-model:value="gotoParams.speed" type="number" :min="1" :max="20" />
                          </div>
                        </div>
                        <Button 
                          type="primary" 
                          @click="sendGotoCommand"
                          :disabled="!canSendCommand || !isGotoValid"
                          :loading="commandLoading"
                          class="w-full"
                        >
                          执行 GOTO
                        </Button>
                      </div>

                      <!-- 高度和速度设置 -->
                      <div class="grid grid-cols-2 gap-4">
                        <div class="p-4 border rounded-lg bg-gray-50">
                          <h4 class="font-medium mb-3">设置高度</h4>
                          <Input 
                            v-model:value="altitudeDisplay" 
                            type="number" 
                            placeholder="高度 (米)"
                            :min="1"
                            :max="500"
                            class="mb-3"
                          />
                          <Button 
                            type="primary" 
                            @click="setAltitude"
                            :disabled="!canSendCommand || !altitudeDisplay"
                            :loading="commandLoading"
                            class="w-full"
                          >
                            设置高度
                          </Button>
                        </div>
                        <div class="p-4 border rounded-lg bg-gray-50">
                          <h4 class="font-medium mb-3">设置速度</h4>
                          <Input 
                            v-model:value="speedDisplay" 
                            type="number" 
                            placeholder="速度 (m/s)"
                            :min="1"
                            :max="20"
                            class="mb-3"
                          />
                          <Button 
                            type="primary" 
                            @click="setSpeed"
                            :disabled="!canSendCommand || !speedDisplay"
                            :loading="commandLoading"
                            class="w-full"
                          >
                            设置速度
                          </Button>
                        </div>
                      </div>
                    </div>
                  </Tabs.TabPane>

                  <!-- 轨迹巡航 -->
                  <Tabs.TabPane key="patrol" tab="轨迹巡航">
                    <div class="space-y-4">
                      <div class="p-4 border rounded-lg bg-gray-50">
                        <h4 class="font-medium mb-3">巡航参数</h4>
                        <div class="grid grid-cols-2 gap-3 mb-3">
                          <div>
                            <label class="block text-sm font-medium mb-1">轨迹类型</label>
                            <Select v-model:value="patrolParams.trajectoryType" class="w-full">
                              <Select.Option value="RECTANGLE">矩形</Select.Option>
                              <Select.Option value="CIRCLE">圆形</Select.Option>
                              <Select.Option value="TRIANGLE">三角形</Select.Option>
                              <Select.Option value="LINE">直线</Select.Option>
                            </Select>
                          </div>
                          <div>
                            <label class="block text-sm font-medium mb-1">大小 (米)</label>
                            <Input v-model:value="patrolParams.size" type="number" :min="10" :max="1000" />
                          </div>
                          <div>
                            <label class="block text-sm font-medium mb-1">高度 (米)</label>
                            <Input v-model:value="patrolParams.altitude" type="number" :min="1" :max="500" />
                          </div>
                          <div>
                            <label class="block text-sm font-medium mb-1">速度 (m/s)</label>
                            <Input v-model:value="patrolParams.speed" type="number" :min="1" :max="20" />
                          </div>
                        </div>
                        <div class="flex space-x-3">
                          <Button 
                            type="primary" 
                            @click="startPatrol"
                            :disabled="!canSendCommand"
                            :loading="commandLoading"
                            class="flex-1"
                          >
                            开始巡航
                          </Button>
                          <Button 
                            @click="stopPatrol"
                            :disabled="!canSendCommand"
                            :loading="commandLoading"
                            class="flex-1"
                          >
                            停止巡航
                          </Button>
                        </div>
                      </div>
                    </div>
                  </Tabs.TabPane>

                  <!-- 原始命令 -->
                  <Tabs.TabPane key="raw" tab="原始命令">
                    <div class="space-y-4">
                      <div class="flex justify-between items-center">
                        <h4 class="font-medium">JSON 命令编辑器</h4>
                        <div class="space-x-2">
                          <Button size="small" @click="loadCommandTemplate">加载模板</Button>
                          <Button size="small" @click="formatCommand">格式化</Button>
                        </div>
                      </div>
                <Input.TextArea
                        v-model:value="rawCommand"
                        :rows="10"
                        placeholder="输入 JSON 格式的命令"
                        class="font-mono"
                />
                      <Button 
                        type="primary" 
                        @click="sendRawCommand"
                        :disabled="!canSendCommand || !rawCommand.trim()"
                        :loading="commandLoading"
                        class="w-full"
                      >
                        发送原始命令
                </Button>
              </div>
                  </Tabs.TabPane>
                </Tabs>
              </Card>

              <!-- 命令历史 -->
              <Card title="命令历史" size="small">
                <div class="flex justify-between items-center mb-3">
                  <span class="text-sm text-gray-600">最近 10 条命令</span>
                  <div class="space-x-2">
                    <Button size="small" @click="loadCommandHistory" :loading="historyLoading">
                      刷新
                  </Button>
                    <Button size="small" @click="clearHistory">
                      清空显示
                  </Button>
                  </div>
                </div>
                <div class="space-y-2 max-h-64 overflow-y-auto">
                  <div 
                    v-for="cmd in commandHistory" 
                    :key="cmd.commandId"
                    class="p-3 border rounded-lg bg-gray-50"
                  >
                    <div class="flex justify-between items-start">
                      <div class="flex-1">
                        <div class="flex items-center space-x-2">
                          <Tag :color="getCommandStatusColor(cmd.status)">
                            {{ formatCommandStatus(cmd.status) }}
                          </Tag>
                          <span class="font-medium">{{ cmd.action }}</span>
                        </div>
                        <div class="text-xs text-gray-500 mt-1">
                          {{ formatTime(cmd.issuedAt) }}
                        </div>
                      </div>
                      <Button 
                        v-if="cmd.status === 'PENDING' || cmd.status === 'SENT'"
                        size="small" 
                        danger 
                        @click="cancelCommand(cmd.commandId)"
                      >
                        取消
                  </Button>
              </div>
            </div>
                  <div v-if="commandHistory.length === 0" class="text-center py-4 text-gray-500">
                    暂无命令历史
                  </div>
                </div>
              </Card>

              <!-- API 响应监控 -->
              <Card title="API 响应监控" size="small">
                <div class="bg-black text-green-400 p-3 rounded font-mono text-xs max-h-32 overflow-y-auto">
                  <pre v-if="lastResponse">{{ lastResponse }}</pre>
                  <div v-else class="text-gray-500">等待 API 响应...</div>
                </div>
              </Card>
            </div>

            <!-- 起飞高度对话框 -->
            <Modal
              v-model:open="takeoffDialogVisible"
              title="设置起飞高度"
              @ok="confirmTakeoff"
              okText="起飞"
              cancelText="取消"
            >
              <div class="space-y-4">
                <div>
                  <label class="block text-sm font-medium mb-2">起飞高度 (米)</label>
                  <Input 
                    v-model:value="takeoffAltitude" 
                    type="number" 
                    :min="1" 
                    :max="100"
                    placeholder="请输入起飞高度"
                  />
                </div>
                <div class="text-xs text-gray-500">
                  建议起飞高度：10-50米
                </div>
              </div>
            </Modal>
          </Tabs.TabPane>

          <!-- 地理围栏标签 -->
          <Tabs.TabPane key="3" tab="地理围栏权限">
            <div class="space-y-6">
              <!-- 当前已分配的地理围栏 -->
              <div>
                <div class="flex items-center justify-between mb-4">
                  <h4 class="text-lg font-medium">已分配的地理围栏</h4>
                  <Button type="primary" @click="loadDroneGeofences" :loading="loadingDroneGeofences">
                    <template #icon><ReloadOutlined /></template>
                    刷新
                  </Button>
                </div>

                <div v-if="droneGeofences.length === 0" class="text-center py-8 text-gray-500">
                  <BorderOutlined class="text-4xl mb-2" />
                  <p>暂无分配的地理围栏</p>
                </div>

                <div v-else class="space-y-3">
                  <div
                    v-for="geofence in droneGeofences"
                    :key="geofence.geofenceId"
                    class="border rounded-lg p-4 bg-gray-50"
                  >
                    <div class="flex items-center justify-between">
                      <div class="flex-1">
                        <div class="flex items-center space-x-2">
                          <h5 class="font-medium">{{ geofence.name }}</h5>
                          <Tag :color="getGeofenceTypeColor(geofence.geofenceType)">
                            {{ getGeofenceTypeText(geofence.geofenceType) }}
                          </Tag>
                          <Tag v-if="geofence.active" color="green">活跃</Tag>
                          <Tag v-else color="red">非活跃</Tag>
                        </div>
                        <p class="text-gray-600 text-sm mt-1">{{ geofence.description || '暂无描述' }}</p>
                        <div class="flex items-center space-x-4 text-xs text-gray-500 mt-2">
                          <span>优先级: {{ geofence.priority }}</span>
                          <span v-if="geofence.areaSquareMeters">
                            面积: {{ formatArea(geofence.areaSquareMeters) }}
                          </span>
                          <span v-if="geofence.altitudeMin || geofence.altitudeMax">
                            高度: {{ geofence.altitudeMin || 0 }}-{{ geofence.altitudeMax || '∞' }}m
                          </span>
                        </div>
                      </div>
                      <Button
                        type="text"
                        danger
                        @click="removeGeofence(geofence.geofenceId)"
                        :loading="removingGeofences.has(geofence.geofenceId)"
                      >
                        <template #icon><DeleteOutlined /></template>
                        移除
                      </Button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 分配新的地理围栏 -->
              <div>
                <div class="flex items-center justify-between mb-4">
                  <h4 class="text-lg font-medium">分配地理围栏</h4>
                  <div class="flex items-center space-x-2">
                    <Select
                      v-model:value="geofenceTypeFilter"
                      placeholder="筛选类型"
                      style="width: 120px"
                      allowClear
                      @change="loadAvailableGeofences"
                    >
                      <Select.Option value="NO_FLY_ZONE">禁飞区</Select.Option>
                      <Select.Option value="FLY_ZONE">允飞区</Select.Option>
                      <Select.Option value="RESTRICTED_ZONE">限制区</Select.Option>
                    </Select>
                    <Switch
                      v-model:checked="showActiveOnly"
                      checkedChildren="仅活跃"
                      unCheckedChildren="全部"
                      @change="loadAvailableGeofences"
                    />
                    <Button @click="loadAvailableGeofences" :loading="loadingAvailableGeofences">
                      <template #icon><ReloadOutlined /></template>
                    </Button>
                  </div>
                </div>

                <div v-if="availableGeofences.length === 0" class="text-center py-8 text-gray-500">
                  <EnvironmentOutlined class="text-4xl mb-2" />
                  <p>暂无可分配的地理围栏</p>
                </div>

                <div v-else class="space-y-3 max-h-96 overflow-y-auto">
                  <div
                    v-for="geofence in filteredAvailableGeofences"
                    :key="geofence.geofenceId"
                    class="border rounded-lg p-4 hover:bg-gray-50 cursor-pointer"
                    :class="{ 'bg-blue-50 border-blue-300': selectedGeofences.has(geofence.geofenceId) }"
                    @click="toggleGeofenceSelection(geofence.geofenceId)"
                  >
                    <div class="flex items-center justify-between">
                      <div class="flex-1">
                        <div class="flex items-center space-x-2">
                          <Checkbox
                            :checked="selectedGeofences.has(geofence.geofenceId)"
                            @change="toggleGeofenceSelection(geofence.geofenceId)"
                          />
                          <h5 class="font-medium">{{ geofence.name }}</h5>
                          <Tag :color="getGeofenceTypeColor(geofence.geofenceType)">
                            {{ getGeofenceTypeText(geofence.geofenceType) }}
                          </Tag>
                          <Tag v-if="geofence.active" color="green">活跃</Tag>
                          <Tag v-else color="red">非活跃</Tag>
                        </div>
                        <p class="text-gray-600 text-sm mt-1">{{ geofence.description || '暂无描述' }}</p>
                        <div class="flex items-center space-x-4 text-xs text-gray-500 mt-2">
                          <span>优先级: {{ geofence.priority }}</span>
                          <span v-if="geofence.areaSquareMeters">
                            面积: {{ formatArea(geofence.areaSquareMeters) }}
                          </span>
                          <span v-if="geofence.altitudeMin || geofence.altitudeMax">
                            高度: {{ geofence.altitudeMin || 0 }}-{{ geofence.altitudeMax || '∞' }}m
                          </span>
                          <span>创建时间: {{ new Date(geofence.createdAt).toLocaleDateString() }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 批量操作按钮 -->
                <div v-if="selectedGeofences.size > 0" class="mt-4 p-4 bg-blue-50 rounded-lg">
                  <div class="flex items-center justify-between">
                    <span class="text-blue-700">
                      已选择 {{ selectedGeofences.size }} 个地理围栏
                    </span>
                    <div class="space-x-2">
                      <Button @click="clearGeofenceSelection">取消选择</Button>
                      <Button type="primary" @click="assignSelectedGeofences" :loading="assigningGeofences">
                        <template #icon><PlusOutlined /></template>
                        分配选中的地理围栏
                      </Button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 所有地理围栏列表 -->
              <div>
                <div class="flex items-center justify-between mb-4">
                  <h4 class="text-lg font-medium">所有地理围栏</h4>
                  <Button @click="loadAllGeofences" :loading="loadingAllGeofences">
                    <template #icon><ReloadOutlined /></template>
                    刷新
                  </Button>
                </div>

                <div v-if="allGeofences.length === 0" class="text-center py-8 text-gray-500">
                  <EnvironmentOutlined class="text-4xl mb-2" />
                  <p v-if="loadingAllGeofences">正在加载地理围栏...</p>
                  <p v-else>暂无地理围栏数据</p>
                </div>

                <div v-else class="space-y-3 max-h-96 overflow-y-auto">
                  <div
                    v-for="geofence in allGeofences"
                    :key="geofence.id"
                    class="border rounded-lg p-4 hover:bg-gray-50"
                    :class="{ 'bg-green-50 border-green-300': isGeofenceAssigned(geofence.id) }"
                  >
                    <div class="flex items-center justify-between">
                      <div class="flex-1">
                        <div class="flex items-center space-x-2">
                          <h5 class="font-medium">{{ geofence.name }}</h5>
                          <Tag :color="getGeofenceTypeColor(geofence.type)">
                            {{ getGeofenceTypeText(geofence.type) }}
                          </Tag>
                          <Tag v-if="geofence.active" color="green">活跃</Tag>
                          <Tag v-else color="red">非活跃</Tag>
                          <Tag v-if="isGeofenceAssigned(geofence.id)" color="blue">已分配</Tag>
                        </div>
                        <p class="text-gray-600 text-sm mt-1">{{ geofence.description || '暂无描述' }}</p>
                        <div class="flex items-center space-x-4 text-xs text-gray-500 mt-2">
                          <span>坐标点: {{ geofence.coordinates.length }} 个</span>
                          <span>创建时间: {{ new Date(geofence.createTime).toLocaleDateString() }}</span>
                          <span v-if="geofence.droneIds">关联无人机: {{ geofence.droneIds.length }} 台</span>
                        </div>
                      </div>
                      <div class="flex space-x-2">
                        <Button
                          v-if="!isGeofenceAssigned(geofence.id)"
                          type="primary"
                          size="small"
                          @click="assignGeofenceFromAll(geofence.id)"
                        >
                          <template #icon><PlusOutlined /></template>
                          分配
                        </Button>
                        <Button
                          v-else
                          type="default"
                          size="small"
                          @click="removeGeofence(geofence.id)"
                          :loading="removingGeofences.has(geofence.id)"
                        >
                          <template #icon><DeleteOutlined /></template>
                          移除
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </Tabs.TabPane>

          <!-- MQTT通信标签 -->
          <Tabs.TabPane key="4" tab="MQTT通信">
            <Descriptions bordered :column="1">
              <Descriptions.Item label="MQTT用户名">{{ selectedDrone.mqtt.username }}</Descriptions.Item>
              <Descriptions.Item label="遥测主题">{{ selectedDrone.mqtt.topicTelemetry }}</Descriptions.Item>
              <Descriptions.Item label="指令主题">{{ selectedDrone.mqtt.topicCommands }}</Descriptions.Item>
            </Descriptions>

            <div class="mt-4">
              <Button type="primary" @click="openMqttModal">
                <template #icon><SendOutlined /></template>
                发送MQTT消息
              </Button>
            </div>
          </Tabs.TabPane>

          <!-- 维护工具标签 -->
          <Tabs.TabPane key="5" tab="维护工具">
            <p class="text-gray-500">此处可添加无人机维护和调试工具</p>
            <div class="mt-4">
              <Button type="primary">
                <template #icon><ToolOutlined /></template>
                固件更新
              </Button>
            </div>
          </Tabs.TabPane>
        </Tabs>

        <!-- 在无人机详情里的描述列表中添加一个新的部分，显示离线信息 -->
        <Descriptions v-if="selectedDrone.status === 'OFFLINE'" title="离线信息" bordered>
          <Descriptions.Item label="离线时间" :span="3">
            {{ selectedDrone.offlineAt ? new Date(selectedDrone.offlineAt).toLocaleString() : '未知' }}
          </Descriptions.Item>
          <Descriptions.Item label="离线原因" :span="3">
            {{ selectedDrone.offlineReason || '未知' }}
          </Descriptions.Item>
          <Descriptions.Item label="操作人" :span="3">
            {{ selectedDrone.offlineBy || '未知' }}
          </Descriptions.Item>
          <Descriptions.Item label="告别信息" :span="3">
            {{ selectedDrone.lastFarewellMessage || '无' }}
          </Descriptions.Item>
        </Descriptions>
      </template>

      <template v-else>
        <p>未选择无人机</p>
      </template>
    </Drawer>

    <!-- MQTT消息对话框 -->
    <Modal
      title="发送MQTT消息"
      :open="mqttModalVisible"
      @ok="sendMqttMessage"
      @cancel="mqttModalVisible = false"
    >
      <Form layout="vertical">
        <Form.Item label="主题">
          <Input v-model:value="mqttForm.topic" readOnly />
        </Form.Item>
        <Form.Item label="消息内容">
          <Input.TextArea v-model:value="mqttForm.message" :rows="4" placeholder="输入MQTT消息内容" />
        </Form.Item>
      </Form>
    </Modal>

    <!-- 下线无人机确认对话框 -->
    <Modal
      v-model:open="offlineModalVisible"
      title="下线无人机确认"
      @ok="handleOfflineDrone"
      :confirmLoading="processingOffline"
      okText="确认下线"
      cancelText="取消"
    >
      <p>您确定要下线此无人机吗？此操作将通知无人机断开连接并终止运行。</p>
      <p>无人机当前状态：<Tag :color="getStatusTag(selectedDrone?.status || 'OFFLINE').color">{{ getStatusTag(selectedDrone?.status || 'OFFLINE').text }}</Tag></p>

      <Form layout="vertical">
        <Form.Item label="下线原因" required>
          <Input
            v-model:value="offlineReason"
            placeholder="请输入下线原因（必填）"
            :maxLength="255"
            showCount
          />
        </Form.Item>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
/* 自定义样式 */
.shadow-md {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

/* 确保信息窗口层级高于图例 */
:deep(.BMap_bubble_pop) {
  z-index: 20 !important;
}

/* 确保抽屉组件在地图上层 */
:deep(.ant-drawer) {
  z-index: 1001;
}
</style>

