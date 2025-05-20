<script lang="ts" setup>
import { ref, onMounted, onUnmounted, computed, reactive, watch } from 'vue';
import { Card, Drawer, Button, Tabs, Descriptions, Tag, Slider, Switch, Input, notification, Space, Modal, Form, Tooltip } from 'ant-design-vue';
import { EyeOutlined, SendOutlined, EnvironmentOutlined, BarsOutlined, WarningOutlined, ClockCircleOutlined, BorderOutlined, ToolOutlined, ExperimentOutlined } from '@ant-design/icons-vue';
import SockJS from 'sockjs-client';
import Stomp from 'webstomp-client';
// @ts-ignore
import axios from 'axios';
// 导入地图组件
import BaiduMap from './BaiduMap.vue';

// 无人机状态类型
type DroneStatus = 'FLYING' | 'IDLE' | 'LOW_BATTERY' | 'TRAJECTORY_ERROR' | 'OFFLINE';

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
const drawerVisible = ref(false);
const selectedDrone = ref<DroneData | null>(null);
const activeTabKey = ref('1');
const commandMessage = ref('');
const geofenceActive = ref(false);
const geofenceRadius = ref(500); // 默认500米
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

// 状态对应的颜色
const statusColors = {
  FLYING: '#1890ff', // 蓝色 - 正常执行任务
  IDLE: '#52c41a',   // 绿色 - 地面待命
  LOW_BATTERY: '#faad14', // 黄色 - 低电量警告
  TRAJECTORY_ERROR: '#ff4d4f', // 红色 - 轨迹异常警告
  OFFLINE: '#d9d9d9' // 灰色 - 离线
};

// 状态对应的中文描述
const statusText = {
  FLYING: '飞行中',
  IDLE: '地面待命',
  LOW_BATTERY: '低电量警告',
  TRAJECTORY_ERROR: '轨迹异常警告',
  OFFLINE: '离线'
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

      // 新无人机通知
      notification.success({
        message: '检测到新无人机',
        description: `已连接到无人机: ${droneId}`,
        duration: 3
      });
    } else {
      // 更新现有无人机记录
      const drone = realDrones.value[droneId];

      // 更新位置和遥测数据
      if (data.latitude !== undefined) drone.position.latitude = data.latitude;
      if (data.longitude !== undefined) drone.position.longitude = data.longitude;
      if (data.altitude !== undefined) drone.position.altitude = data.altitude;
      if (data.speed !== undefined) drone.speed = data.speed;
      if (data.batteryLevel !== undefined) drone.batteryPercentage = data.batteryLevel;
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
onMounted(() => {
  console.log('组件已挂载，初始化中...');

  // 重置组件标志
  isComponentMounted.value = true;
  mapScriptLoaded.value = false;

  // 初始化地图
  initBaiduMap();

  // 尝试检测后端API是否可用
  checkBackendAvailability().then(available => {
    // 确保组件仍然挂载
    if (!isComponentMounted.value) return;

    if (available) {
      // 后端可用，尝试连接WebSocket
      initWebSocket();
    } else {
      console.log('后端API不可用，请检查后端服务');
      notification.error({
        message: '后端连接失败',
        description: '无法连接到后端服务，请确保后端服务和Python无人机模拟器正在运行'
      });
    }
  });

  // 设置定时器，每15秒检查一次过期数据
  staleCheckInterval = setInterval(() => {
    if (isComponentMounted.value) {
      checkStaleData();
    }
  }, 15000) as unknown as number;

  console.log('组件初始化完成');
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
            <div class="w-4 h-4 rounded-full mr-2" style="background-color: #d9d9d9;"></div>
            <span>离线</span>
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
            <div class="space-y-4">
              <div>
                <p class="mb-2 font-medium">发送控制指令:</p>
                <Input.TextArea
                  v-model:value="commandMessage"
                  placeholder="输入控制指令"
                  :rows="4"
                  class="mb-2"
                />
                <Button type="primary" @click="sendCommand">
                  <template #icon><SendOutlined /></template>
                  发送指令
                </Button>
              </div>

              <div class="mt-4">
                <p class="mb-2 font-medium">预设指令:</p>
                <Space>
                  <Button @click="commandMessage = JSON.stringify({ action: 'GOTO_HOME', parameters: {} })">
                    返航
                  </Button>
                  <Button @click="commandMessage = JSON.stringify({ action: 'LAND', parameters: {} })">
                    降落
                  </Button>
                  <Button @click="commandMessage = JSON.stringify({ action: 'HOVER', parameters: { duration: 30 } })">
                    悬停
                  </Button>
                </Space>
              </div>
            </div>
          </Tabs.TabPane>

          <!-- 地理围栏标签 -->
          <Tabs.TabPane key="3" tab="地理围栏">
            <div class="space-y-4">
              <div class="flex items-center justify-between">
                <span class="font-medium">地理围栏状态:</span>
                <Switch :checked="geofenceActive" @change="toggleGeofence" />
              </div>

              <div>
                <p class="mb-2 font-medium">围栏半径: {{ geofenceRadius }}米</p>
                <Slider
                  v-model:value="geofenceRadius"
                  :min="100"
                  :max="2000"
                  :step="100"
                  :disabled="!geofenceActive"
                />
              </div>

              <div class="mt-4">
                <Button type="primary" :disabled="!geofenceActive" @click="toggleGeofence">
                  <template #icon><BorderOutlined /></template>
                  应用围栏
                </Button>
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
