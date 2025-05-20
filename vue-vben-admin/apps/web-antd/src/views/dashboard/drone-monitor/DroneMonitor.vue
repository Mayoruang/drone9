<script lang="ts" setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue';
import { Card, Drawer, Button, Tabs, Descriptions, Tag, Slider, Switch, Input, notification, Space, Modal, Form, Tooltip } from 'ant-design-vue';
import { EyeOutlined, SendOutlined, EnvironmentOutlined, BarsOutlined, WarningOutlined, ClockCircleOutlined, BorderOutlined, ToolOutlined } from '@ant-design/icons-vue';
import type { FormInstance } from 'ant-design-vue';

// 引入状态管理和服务
import { useDroneStore, statusColors, statusText } from '../../../store/modules/drone';
import { getMapService } from '../../../services/map';
import { getWebSocketService } from '../../../services/websocket';

// 组件状态
const loading = ref(false);
const droneStore = useDroneStore();
const drawerVisible = ref(false);
const activeTabKey = ref('1');
const commandMessage = ref('');
const geofenceActive = ref(false);
const geofenceRadius = ref(500); // 默认500米
const mqttModalVisible = ref(false);
const offlineModalVisible = ref(false);
const offlineReason = ref('');
const processingOffline = ref(false);
const mapContainerId = 'droneMonitorMap';

// MQTT表单
const mqttForm = ref({
  topic: '',
  message: ''
});

// 离线表单
const offlineForm = ref<FormInstance>();

// 获取活跃无人机列表和选中的无人机
const activeDrones = computed(() => droneStore.activeDrones);
const selectedDrone = computed(() => droneStore.selectedDrone);

// 地图和WebSocket服务
const mapService = getMapService();
const websocketService = getWebSocketService();

// 获取状态标签样式
const getStatusTag = (status: string) => {
  const color = statusColors[status as keyof typeof statusColors] || statusColors.OFFLINE;
  const text = statusText[status as keyof typeof statusText] || '未知';
  return { color, text };
};

// 获取电池颜色
const getBatteryColor = (percentage: number) => {
  if (percentage <= 20) return '#ff4d4f';
  if (percentage <= 40) return '#faad14';
  return '#52c41a';
};

// 初始化地图和WebSocket连接
const initializeServices = async () => {
  loading.value = true;

  try {
    // 初始化地图
    const mapInitialized = await mapService.init(mapContainerId, {
      center: { lng: 123.4315, lat: 41.8057 }, // 沈阳中心
      zoom: 12,
      showControls: true
    });

    if (!mapInitialized) {
      notification.error({
        message: '地图初始化失败',
        description: '无法加载百度地图，请刷新页面重试'
      });
    }

    // 连接WebSocket
    const wsConnected = await websocketService.connect();

    if (!wsConnected) {
      notification.warning({
        message: 'WebSocket连接失败',
        description: '无法连接到实时数据服务，将自动重试连接'
      });
    }

    // 设置加载状态
    loading.value = false;

    // 启动定时检查过期数据任务
    startStaleDataCheck();
  } catch (error) {
    console.error('初始化服务失败', error);
    notification.error({
      message: '初始化失败',
      description: '服务初始化过程中发生错误，请刷新页面重试'
    });
    loading.value = false;
  }
};

// 处理无人机标记点击
const handleDroneSelect = (drone: any) => {
  droneStore.selectDrone(drone.droneId);
  drawerVisible.value = true;
};

// 渲染无人机标记
const renderDroneMarkers = () => {
  // 使用地图服务更新标记
  mapService.updateDroneMarkers(activeDrones.value, handleDroneSelect);
};

// 关闭抽屉
const closeDrawer = () => {
  drawerVisible.value = false;
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
  if (!selectedDrone.value) return;

  // 切换围栏状态
  geofenceActive.value = !geofenceActive.value;

  if (geofenceActive.value) {
    // 添加围栏
    mapService.addGeofence(
      selectedDrone.value.droneId,
      selectedDrone.value.position,
      geofenceRadius.value
    );

    notification.info({
      message: '地理围栏已启用',
      description: `已为无人机 ${selectedDrone.value.serialNumber} 设置${geofenceRadius.value}米半径的地理围栏`,
    });
  } else {
    // 移除围栏
    mapService.removeGeofence(selectedDrone.value.droneId);

    notification.info({
      message: '地理围栏已禁用',
      description: `已为无人机 ${selectedDrone.value.serialNumber} 禁用地理围栏`,
    });
  }
};

// 打开MQTT消息对话框
const openMqttModal = () => {
  if (!selectedDrone.value) return;

  mqttForm.value.topic = selectedDrone.value.mqtt?.topicCommands || '';
  mqttForm.value.message = '';
  mqttModalVisible.value = true;
};

// 发送MQTT消息
const sendMqttMessage = () => {
  if (!selectedDrone.value || !mqttForm.value.message) {
    notification.warning({
      message: '发送失败',
      description: '请输入消息内容',
    });
    return;
  }

  // 模拟MQTT消息发送
  notification.success({
    message: 'MQTT消息已发送',
    description: `主题: ${mqttForm.value.topic}, 消息: ${mqttForm.value.message}`,
  });

  // 关闭对话框
  mqttModalVisible.value = false;
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
  offlineModalVisible.value = true;
};

// 处理下线无人机
const handleOfflineDrone = async () => {
  if (!selectedDrone.value) return;

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

  // 执行下线操作
  processingOffline.value = true;

  try {
    const success = await droneStore.setDroneOffline(
      selectedDrone.value.droneId,
      offlineReason.value,
      10
    );

    if (success) {
      // 关闭对话框和抽屉
      offlineModalVisible.value = false;
      drawerVisible.value = false;
    }
  } catch (error) {
    console.error('下线无人机出错:', error);
  } finally {
    processingOffline.value = false;
  }
};

// 定时检查过期数据
let staleCheckInterval: number | null = null;
const startStaleDataCheck = () => {
  // 设置定时器，每15秒检查一次过期数据
  staleCheckInterval = window.setInterval(() => {
    droneStore.checkStaleData();
  }, 15000) as unknown as number;
};

// 监听无人机数据变化，更新地图标记
watch(() => activeDrones.value, () => {
  nextTick(() => {
    renderDroneMarkers();
  });
}, { deep: true });

// 生命周期钩子
onMounted(() => {
  // 初始化服务
  initializeServices();
});

onUnmounted(() => {
  // 清理资源
  if (staleCheckInterval !== null) {
    clearInterval(staleCheckInterval);
    staleCheckInterval = null;
  }

  // 断开WebSocket
  websocketService.disconnect();

  // 销毁地图
  mapService.destroy();
});
</script>

<template>
  <div class="p-5">
    <Card title="无人机状态监控" :loading="loading" class="shadow-md" :bodyStyle="{ padding: 0 }">
      <!-- 地图容器 -->
      <div :id="mapContainerId" style="width: 100%; height: 700px;"></div>

      <!-- 状态图例 -->
      <div class="absolute top-16 right-8 bg-white p-3 shadow-md rounded-md z-10">
        <h4 class="text-base font-medium mb-2">无人机状态</h4>
        <div class="flex flex-col space-y-2">
          <div v-for="(color, status) in statusColors" :key="status" class="flex items-center">
            <div class="w-4 h-4 rounded-full mr-2" :style="{ backgroundColor: color }"></div>
            <span>{{ statusText[status] }}</span>
          </div>
        </div>
      </div>
    </Card>

    <!-- 无人机详情抽屉 -->
    <Drawer
      v-model:open="drawerVisible"
      :title="`无人机详情 - ${selectedDrone?.serialNumber || ''}`"
      width="500"
      placement="right"
      :closable="true"
      @close="closeDrawer"
    >
      <template #extra>
        <Space>
          <Tooltip :title="selectedDrone && selectedDrone.status !== 'IDLE' ? '只有处于地面待命状态的无人机才能被下线' : ''">
            <Button
              danger
              @click="showOfflineModal"
              :disabled="!selectedDrone || selectedDrone.status === 'OFFLINE' || selectedDrone.status !== 'IDLE'"
            >
              下线
            </Button>
          </Tooltip>
          <Button type="default" @click="closeDrawer">
            关闭
          </Button>
        </Space>
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
          <Tabs.TabPane key="4" tab="MQTT通信" v-if="selectedDrone.mqtt">
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

        <!-- 离线信息 -->
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
      <p v-if="selectedDrone">无人机当前状态：<Tag :color="getStatusTag(selectedDrone.status).color">{{ getStatusTag(selectedDrone.status).text }}</Tag></p>

      <Form ref="offlineForm" layout="vertical">
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
