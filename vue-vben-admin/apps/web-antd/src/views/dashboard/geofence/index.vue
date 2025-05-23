<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { PlusOutlined } from '@ant-design/icons-vue';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  notification,
  Radio,
  Select,
  Space,
} from 'ant-design-vue';

import GeofenceList from './components/GeofenceList.vue';
import GeofenceMap from './components/GeofenceMap.vue';

// 地理围栏类型定义
interface GeofenceData {
  id: string;
  name: string;
  type: 'FLY_ZONE' | 'NO_FLY_ZONE'; // 禁飞区或允飞区
  coordinates: Array<{ lat: number; lng: number }>;
  description?: string;
  createTime: string;
  thumbnail?: string; // 缩略图
  droneIds?: string[]; // 关联的无人机ID列表
}

// 无人机数据类型（用于关联选择）
interface DroneData {
  id: string;
  serialNumber: string;
  name: string;
}

// 状态管理
const loading = ref(false);
const mapRef = ref<any>(null);
const isDrawing = ref(false);
const showGeofenceModal = ref(false);
const editingGeofence = ref<GeofenceData | null>(null);

// 表单数据
const geofenceForm = reactive({
  name: '',
  type: 'NO_FLY_ZONE' as 'FLY_ZONE' | 'NO_FLY_ZONE',
  description: '',
  droneIds: [] as string[],
});

// Mock数据 - 地理围栏列表
const geofenceList = ref<GeofenceData[]>([
  {
    id: '1',
    name: '沈阳浑南禁飞区',
    type: 'NO_FLY_ZONE',
    coordinates: [
      { lng: 123.44, lat: 41.72 },
      { lng: 123.46, lat: 41.72 },
      { lng: 123.46, lat: 41.74 },
      { lng: 123.44, lat: 41.74 },
    ],
    description: '沈阳浑南新区核心区域，禁止无人机飞行',
    createTime: '2024-01-15 10:30:00',
    droneIds: ['drone001', 'drone002'],
  },
  {
    id: '2',
    name: '机场周边允飞区',
    type: 'FLY_ZONE',
    coordinates: [
      { lng: 123.4, lat: 41.7 },
      { lng: 123.42, lat: 41.7 },
      { lng: 123.42, lat: 41.72 },
      { lng: 123.4, lat: 41.72 },
    ],
    description: '指定的无人机飞行训练区域',
    createTime: '2024-01-16 14:20:00',
    droneIds: ['drone003'],
  },
]);

// Mock数据 - 无人机列表
const droneList = ref<DroneData[]>([
  { id: 'drone001', serialNumber: 'DJI001', name: '大疆 Mavic Air 2' },
  { id: 'drone002', serialNumber: 'DJI002', name: '大疆 Mini 3 Pro' },
  { id: 'drone003', serialNumber: 'DJI003', name: '大疆 Phantom 4' },
  { id: 'drone004', serialNumber: 'AUTEL001', name: 'Autel EVO II' },
  { id: 'drone005', serialNumber: 'YUNEEC001', name: 'Yuneec Typhoon H' },
]);

// 开始绘制地理围栏
const startDrawing = () => {
  if (isDrawing.value) {
    notification.warning({
      message: '正在绘制中',
      description: '请完成当前绘制或按ESC键取消',
    });
    return;
  }

  isDrawing.value = true;
  mapRef.value?.startDrawPolygon();

  notification.info({
    message: '开始绘制地理围栏',
    description: '在地图上点击绘制多边形，双击结束绘制',
  });
};

// 绘制完成回调
const onDrawComplete = (coordinates: Array<{ lat: number; lng: number }>) => {
  isDrawing.value = false;

  if (coordinates.length < 3) {
    notification.error({
      message: '绘制失败',
      description: '多边形至少需要3个点',
    });
    return;
  }

  // 显示设置对话框
  showGeofenceModal.value = true;
  // 重置表单
  Object.assign(geofenceForm, {
    name: '',
    type: 'NO_FLY_ZONE',
    description: '',
    droneIds: [],
  });

  // 临时存储坐标
  editingGeofence.value = {
    id: '',
    name: '',
    type: 'NO_FLY_ZONE',
    coordinates,
    createTime: '',
    droneIds: [],
  };
};

// 取消绘制
const cancelDrawing = () => {
  isDrawing.value = false;
  mapRef.value?.cancelDraw();
  notification.info({
    message: '已取消绘制',
  });
};

// 保存地理围栏
const saveGeofence = () => {
  if (!geofenceForm.name.trim()) {
    notification.error({
      message: '请输入地理围栏名称',
    });
    return;
  }

  if (!editingGeofence.value) {
    notification.error({
      message: '绘制数据丢失，请重新绘制',
    });
    return;
  }

  const newGeofence: GeofenceData = {
    ...editingGeofence.value,
    id: Date.now().toString(),
    name: geofenceForm.name,
    type: geofenceForm.type,
    description: geofenceForm.description,
    createTime: new Date().toLocaleString(),
    droneIds: geofenceForm.droneIds,
  };

  geofenceList.value.unshift(newGeofence);

  // 在地图上添加围栏
  mapRef.value?.addGeofence(newGeofence);

  notification.success({
    message: '地理围栏创建成功',
    description: `${newGeofence.name} 已添加到系统`,
  });

  showGeofenceModal.value = false;
  editingGeofence.value = null;
};

// 取消地理围栏设置
const cancelGeofenceForm = () => {
  // 关闭模态框
  showGeofenceModal.value = false;

  // 清除临时存储的围栏数据
  editingGeofence.value = null;

  // 使用强力清理方法确保完全清除所有绘制内容
  mapRef.value?.forceClearAll();

  // 重置绘制状态
  isDrawing.value = false;

  notification.info({
    message: '已取消围栏设置',
    description: '绘制的围栏已撤销',
  });
};

// 删除地理围栏
const deleteGeofence = (id: string) => {
  const index = geofenceList.value.findIndex((item) => item.id === id);
  if (index !== -1) {
    const geofence = geofenceList.value[index];
    if (geofence) {
      geofenceList.value.splice(index, 1);

      // 从地图移除
      mapRef.value?.removeGeofence(id);

      notification.success({
        message: '删除成功',
        description: `地理围栏 "${geofence.name}" 已删除`,
      });
    }
  }
};

// 定位到地理围栏
const locateGeofence = (geofence: GeofenceData) => {
  mapRef.value?.focusGeofence(geofence);

  notification.info({
    message: '地图已定位',
    description: `已定位到地理围栏 "${geofence.name}"`,
  });
};

// 获取地理围栏统计
const geofenceStats = computed(() => {
  const noFlyZones = geofenceList.value.filter(
    (item) => item.type === 'NO_FLY_ZONE',
  ).length;
  const flyZones = geofenceList.value.filter(
    (item) => item.type === 'FLY_ZONE',
  ).length;

  return {
    total: geofenceList.value.length,
    noFlyZones,
    flyZones,
  };
});

// 键盘事件处理
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && isDrawing.value) {
    cancelDrawing();
  }
};

onMounted(() => {
  document.addEventListener('keydown', handleKeydown);
});
</script>

<template>
  <div class="h-full p-5">
    <!-- 页面标题和工具栏 -->
    <div class="mb-5">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-2xl font-bold text-gray-800">地理围栏管理</h2>
          <p class="mt-1 text-gray-600">设置和管理无人机飞行区域限制</p>
        </div>
        <div class="flex items-center space-x-4">
          <div class="text-sm text-gray-500">
            总计: {{ geofenceStats.total }} | 禁飞区:
            {{ geofenceStats.noFlyZones }} | 允飞区:
            {{ geofenceStats.flyZones }}
          </div>
          <Button
            type="primary"
            size="large"
            :loading="isDrawing"
            @click="startDrawing"
            :disabled="isDrawing"
          >
            <template #icon><PlusOutlined /></template>
            {{ isDrawing ? '绘制中...' : '开始绘制围栏' }}
          </Button>
          <Button v-if="isDrawing" danger size="large" @click="cancelDrawing">
            取消绘制 (ESC)
          </Button>
        </div>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="flex h-[calc(100vh-200px)] gap-5">
      <!-- 左侧地图区域 -->
      <div class="flex-1">
        <Card
          title="地理围栏地图"
          :loading="loading"
          class="h-full shadow-md"
          :body-style="{ padding: 0, height: 'calc(100% - 57px)' }"
        >
          <template #extra>
            <Space>
              <span class="text-sm text-gray-500">
                {{ isDrawing ? '绘制模式' : '查看模式' }}
              </span>
            </Space>
          </template>

          <GeofenceMap
            ref="mapRef"
            :geofences="geofenceList"
            :is-drawing="isDrawing"
            @draw-complete="onDrawComplete"
            @geofence-click="locateGeofence"
            class="h-full"
          />
        </Card>
      </div>

      <!-- 右侧列表区域 -->
      <div class="w-80">
        <GeofenceList
          :geofences="geofenceList"
          :loading="loading"
          @locate="locateGeofence"
          @delete="deleteGeofence"
        />
      </div>
    </div>

    <!-- 地理围栏设置模态框 -->
    <Modal
      v-model:open="showGeofenceModal"
      title="设置地理围栏"
      width="500px"
      @ok="saveGeofence"
      @cancel="cancelGeofenceForm"
      ok-text="保存"
      cancel-text="取消"
    >
      <Form layout="vertical" class="mt-4">
        <Form.Item label="围栏名称" required>
          <Input
            v-model:value="geofenceForm.name"
            placeholder="请输入地理围栏名称"
            :max-length="50"
            show-count
          />
        </Form.Item>

        <Form.Item label="围栏类型" required>
          <Radio.Group v-model:value="geofenceForm.type">
            <Radio value="NO_FLY_ZONE">
              <span class="text-red-600">禁飞区</span>
              <span class="ml-2 text-sm text-gray-500">限制无人机进入</span>
            </Radio>
            <Radio value="FLY_ZONE">
              <span class="text-green-600">允飞区</span>
              <span class="ml-2 text-sm text-gray-500">允许无人机飞行</span>
            </Radio>
          </Radio.Group>
        </Form.Item>

        <Form.Item label="关联无人机">
          <Select
            v-model:value="geofenceForm.droneIds"
            mode="multiple"
            placeholder="选择要应用此围栏的无人机"
            :options="
              droneList.map((drone) => ({
                value: drone.id,
                label: `${drone.serialNumber} - ${drone.name}`,
              }))
            "
            allow-clear
          />
        </Form.Item>

        <Form.Item label="描述">
          <Input.TextArea
            v-model:value="geofenceForm.description"
            placeholder="请输入围栏描述（可选）"
            :rows="3"
            :max-length="200"
            show-count
          />
        </Form.Item>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
/* 自定义样式 */
.shadow-md {
  box-shadow:
    0 4px 6px -1px rgb(0 0 0 / 10%),
    0 2px 4px -1px rgb(0 0 0 / 6%);
}

/* 确保模态框在地图之上 */
:deep(.ant-modal) {
  z-index: 1001;
}

/* 确保抽屉组件在地图上层 */
:deep(.ant-drawer) {
  z-index: 1001;
}
</style>
