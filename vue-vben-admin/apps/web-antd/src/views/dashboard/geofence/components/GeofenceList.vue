<script lang="ts" setup>
import { computed, defineEmits, defineProps, ref } from 'vue';

import {
  ClockCircleOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons-vue';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  List,
  Modal,
  Space,
  Tag,
  Tooltip,
} from 'ant-design-vue';

// 地理围栏数据接口
interface GeofenceData {
  id: string;
  name: string;
  type: 'FLY_ZONE' | 'NO_FLY_ZONE';
  coordinates: Array<{ lat: number; lng: number }>;
  description?: string;
  createTime: string;
  thumbnail?: string;
  droneIds?: string[];
}

// 定义组件属性
const props = defineProps<{
  geofences: GeofenceData[];
  loading?: boolean;
}>();

// 定义事件
const emit = defineEmits<{
  (e: 'locate', geofence: GeofenceData): void;
  (e: 'delete', id: string): void;
  (e: 'view', geofence: GeofenceData): void;
}>();

// 状态管理
const deleteModal = ref({
  visible: false,
  geofence: null as GeofenceData | null,
  loading: false,
});

const detailModal = ref({
  visible: false,
  geofence: null as GeofenceData | null,
});

// 计算属性
const sortedGeofences = computed(() => {
  return [...props.geofences].sort((a, b) => {
    return new Date(b.createTime).getTime() - new Date(a.createTime).getTime();
  });
});

// 获取围栏类型样式
const getGeofenceTypeConfig = (type: 'FLY_ZONE' | 'NO_FLY_ZONE') => {
  return type === 'NO_FLY_ZONE'
    ? {
        color: '#ff4d4f',
        text: '禁飞区',
        bgColor: '#fff2f0',
        icon: '🚫',
      }
    : {
        color: '#52c41a',
        text: '允飞区',
        bgColor: '#f6ffed',
        icon: '✅',
      };
};

// 生成缩略图SVG
const generateThumbnail = (geofence: GeofenceData) => {
  const config = getGeofenceTypeConfig(geofence.type);
  const coords = geofence.coordinates;

  if (coords.length < 3) return '';

  // 直接使用改进后的真实地图缩略图方案
  return generateRealMapThumbnail(geofence, false);
};

// 生成真实地图缩略图（使用百度地图静态图作为背景）
const generateRealMapThumbnail = (geofence: GeofenceData, isLarge: boolean = false) => {
  const config = getGeofenceTypeConfig(geofence.type);
  const coords = geofence.coordinates;

  if (coords.length < 3) return '';

  // 计算边界框
  const lngs = coords.map((c) => c.lng);
  const lats = coords.map((c) => c.lat);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);

  // 计算地理中心点
  const centerLng = (minLng + maxLng) / 2;
  const centerLat = (minLat + maxLat) / 2;

  // 考虑纬度修正，避免形状拉伸
  const latCorrection = Math.cos((centerLat * Math.PI) / 180);

  // 计算真实的地理跨度
  const lngSpan = (maxLng - minLng) * latCorrection;
  const latSpan = maxLat - minLat;
  const maxSpan = Math.max(lngSpan, latSpan);

  // SVG参数
  const svgSize = isLarge ? 200 : 100;
  const padding = isLarge ? 15 : 8;
  const availableSize = svgSize - 2 * padding;

  // 归一化坐标到SVG坐标系，保持真实比例
  const normalizedCoords = coords.map((coord) => {
    const normalizedLng = ((coord.lng - minLng) * latCorrection) / maxSpan;
    const normalizedLat = (maxLat - coord.lat) / maxSpan;

    return {
      x: padding + normalizedLng * availableSize,
      y: padding + normalizedLat * availableSize,
    };
  });

  const pathData = `${normalizedCoords
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
    .join(' ')} Z`;

  // 生成百度地图静态图作为背景
  const zoom = calculateOptimalZoom(lngSpan, latSpan);
  const mapBackground = `https://api.map.baidu.com/staticimage/v2?ak=PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE&center=${centerLng},${centerLat}&width=${svgSize}&height=${svgSize}&zoom=${zoom}&scale=2`;

  return `
    <div style="position: relative; width: 100%; height: 100%; background: #f0f9ff; display: flex; align-items: center; justify-content: center; border-radius: ${isLarge ? '8px' : '4px'}; overflow: hidden;">
      <!-- 真实地图背景 -->
      <img
        src="${mapBackground}"
        style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; opacity: 0.8;"
        alt="地图背景"
        onError="this.style.display='none'; this.nextElementSibling.style.display='block';"
      />

      <!-- 备用背景（当地图加载失败时显示） -->
      <div style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: linear-gradient(45deg, #f0f9ff 0%, #e0f7fa 100%); display: none;">
        <svg width="100%" height="100%" viewBox="0 0 ${svgSize} ${svgSize}">
          <defs>
            <pattern id="grid-fallback-${geofence.id}" width="15" height="15" patternUnits="userSpaceOnUse">
              <path d="M 15 0 L 0 0 0 15" fill="none" stroke="#e1f5fe" stroke-width="0.5"/>
            </pattern>
          </defs>
          <rect width="${svgSize}" height="${svgSize}" fill="url(#grid-fallback-${geofence.id})"/>
        </svg>
      </div>

      <!-- 地理围栏覆盖层 -->
      <svg width="100%" height="100%" viewBox="0 0 ${svgSize} ${svgSize}" style="position: absolute; top: 0; left: 0;">
        <defs>
          <filter id="shadow-${geofence.id}" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="1" dy="1" stdDeviation="1" flood-color="#000000" flood-opacity="0.4"/>
          </filter>
        </defs>

        <!-- 地理围栏区域 -->
        <path
          d="${pathData}"
          fill="${config.color}"
          fill-opacity="0.3"
          stroke="${config.color}"
          stroke-width="${isLarge ? '2.5' : '2'}"
          filter="url(#shadow-${geofence.id})"
        />

        <!-- 围栏顶点标记 -->
        ${normalizedCoords.map((point, index) => `
          <circle cx="${point.x}" cy="${point.y}" r="${isLarge ? '2' : '1.5'}"
                  fill="white" stroke="${config.color}" stroke-width="${isLarge ? '1.5' : '1'}"
                  opacity="0.9"/>
        `).join('')}
      </svg>
    </div>
  `;
};

// 计算最佳缩放级别
const calculateOptimalZoom = (lngSpan: number, latSpan: number) => {
  const maxSpan = Math.max(lngSpan, latSpan);

  if (maxSpan > 0.5) return 8;
  if (maxSpan > 0.2) return 9;
  if (maxSpan > 0.1) return 10;
  if (maxSpan > 0.05) return 11;
  if (maxSpan > 0.02) return 12;
  if (maxSpan > 0.01) return 13;
  if (maxSpan > 0.005) return 14;
  if (maxSpan > 0.002) return 15;
  return 16;
};

// 生成大尺寸缩略图（用于详情页面）
const generateLargeThumbnail = (geofence: GeofenceData) => {
  const config = getGeofenceTypeConfig(geofence.type);
  const coords = geofence.coordinates;

  if (coords.length < 3) return '';

  // 直接使用改进后的大尺寸真实地图缩略图方案
  return generateRealMapThumbnail(geofence, true);
};

// 格式化时间
const formatTime = (timeStr: string) => {
  try {
    const date = new Date(timeStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (days === 0) {
      return `今天 ${date.toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
      })}`;
    } else if (days === 1) {
      return `昨天 ${date.toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
      })}`;
    } else if (days < 7) {
      return `${days}天前`;
    } else {
      return `${date.toLocaleDateString('zh-CN')} ${date.toLocaleTimeString(
        'zh-CN',
        {
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
        },
      )}`;
    }
  } catch {
    return timeStr;
  }
};

// 获取坐标摘要
const getCoordinatesSummary = (
  coordinates: Array<{ lat: number; lng: number }>,
) => {
  if (coordinates.length === 0) return '无坐标';

  const lngs = coordinates.map((c) => c.lng);
  const lats = coordinates.map((c) => c.lat);
  const centerLng = (Math.min(...lngs) + Math.max(...lngs)) / 2;
  const centerLat = (Math.min(...lats) + Math.max(...lats)) / 2;

  return `${centerLat.toFixed(4)}, ${centerLng.toFixed(4)}`;
};

// 定位到地理围栏
const handleLocate = (geofence: GeofenceData) => {
  emit('locate', geofence);
};

// 查看详情
const handleView = (geofence: GeofenceData) => {
  detailModal.value = {
    visible: true,
    geofence,
  };
};

// 关闭详情模态框
const closeDetailModal = () => {
  detailModal.value = {
    visible: false,
    geofence: null,
  };
};

// 显示删除确认对话框
const showDeleteModal = (geofence: GeofenceData) => {
  deleteModal.value = {
    visible: true,
    geofence,
    loading: false,
  };
};

// 确认删除
const confirmDelete = async () => {
  if (!deleteModal.value.geofence) return;

  deleteModal.value.loading = true;

  // 模拟删除延迟
  await new Promise((resolve) => setTimeout(resolve, 500));

  emit('delete', deleteModal.value.geofence.id);

  deleteModal.value = {
    visible: false,
    geofence: null,
    loading: false,
  };
};

// 取消删除
const cancelDelete = () => {
  deleteModal.value = {
    visible: false,
    geofence: null,
    loading: false,
  };
};
</script>

<template>
  <Card
    title="地理围栏列表"
    :loading="loading"
    class="h-full shadow-md"
    :body-style="{
      padding: 0,
      height: 'calc(100% - 57px)',
      overflow: 'hidden',
    }"
  >
    <template #extra>
      <span class="text-sm text-gray-500"> 共 {{ geofences.length }} 个 </span>
    </template>

    <div class="h-full overflow-y-auto">
      <List :data-source="sortedGeofences" :loading="loading" class="p-0">
        <template #renderItem="{ item: geofence }">
          <List.Item
            class="border-b border-gray-100 transition-colors duration-200 hover:bg-gray-50"
          >
            <div class="w-full p-4">
              <!-- 头部信息 -->
              <div class="mb-3 flex items-start justify-between">
                <div class="min-w-0 flex-1">
                  <div class="mb-1 flex items-center">
                    <h4
                      class="mr-2 truncate text-base font-medium text-gray-900"
                    >
                      {{ geofence.name }}
                    </h4>
                    <Tag
                      :color="getGeofenceTypeConfig(geofence.type).color"
                      class="text-xs"
                    >
                      {{ getGeofenceTypeConfig(geofence.type).icon }}
                      {{ getGeofenceTypeConfig(geofence.type).text }}
                    </Tag>
                  </div>
                  <p class="line-clamp-2 text-sm text-gray-500">
                    {{ geofence.description || '暂无描述' }}
                  </p>
                </div>
              </div>

              <!-- 缩略图区域 -->
              <div class="mb-3">
                <div
                  class="h-24 w-full cursor-pointer overflow-hidden rounded-lg border-2 transition-shadow hover:shadow-md"
                  :style="{
                    borderColor: `${getGeofenceTypeConfig(geofence.type).color}40`,
                  }"
                  @click="handleLocate(geofence)"
                >
                  <div
                    class="flex h-full w-full items-center justify-center"
                    :style="{
                      backgroundColor: getGeofenceTypeConfig(geofence.type)
                        .bgColor,
                    }"
                  >
                    <div
                      v-html="generateThumbnail(geofence)"
                      class="h-full w-full"
                    ></div>
                  </div>
                </div>
                <div class="mt-1 text-center text-xs text-gray-400">
                  点击缩略图定位
                </div>
              </div>

              <!-- 详细信息 -->
              <div class="mb-3 space-y-2 text-xs text-gray-600">
                <div class="flex items-center">
                  <EnvironmentOutlined class="mr-1" />
                  <span
                    >中心点:
                    {{ getCoordinatesSummary(geofence.coordinates) }}</span
                  >
                </div>
                <div class="flex items-center">
                  <InfoCircleOutlined class="mr-1" />
                  <span>顶点数: {{ geofence.coordinates.length }}</span>
                </div>
                <div class="flex items-center">
                  <ClockCircleOutlined class="mr-1" />
                  <span>创建时间: {{ formatTime(geofence.createTime) }}</span>
                </div>
                <div
                  v-if="geofence.droneIds && geofence.droneIds.length > 0"
                  class="flex items-center"
                >
                  <span class="mr-1">🚁</span>
                  <span>关联无人机: {{ geofence.droneIds.length }} 台</span>
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="flex space-x-2">
                <Tooltip title="查看详情">
                  <Button
                    type="primary"
                    size="small"
                    @click="handleView(geofence)"
                    class="flex-1"
                  >
                    <template #icon><EyeOutlined /></template>
                    详情
                  </Button>
                </Tooltip>

                <Tooltip title="删除围栏">
                  <Button
                    danger
                    size="small"
                    @click="showDeleteModal(geofence)"
                    class="flex-1"
                  >
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </Button>
                </Tooltip>
              </div>
            </div>
          </List.Item>
        </template>

        <template #loadMore>
          <div
            v-if="geofences.length === 0 && !loading"
            class="py-8 text-center text-gray-500"
          >
            <EnvironmentOutlined class="mb-2 text-4xl text-gray-300" />
            <p>暂无地理围栏</p>
            <p class="text-sm">点击上方"开始绘制围栏"按钮创建第一个围栏</p>
          </div>
        </template>
      </List>
    </div>

    <!-- 删除确认对话框 -->
    <Modal
      v-model:open="deleteModal.visible"
      title="确认删除"
      @ok="confirmDelete"
      @cancel="cancelDelete"
      :confirm-loading="deleteModal.loading"
      ok-text="确认删除"
      cancel-text="取消"
      ok-type="danger"
    >
      <div v-if="deleteModal.geofence">
        <p class="mb-3">您确定要删除以下地理围栏吗？此操作不可恢复。</p>

        <div class="rounded-lg bg-gray-50 p-3">
          <div class="mb-2 flex items-center">
            <span class="font-medium">{{ deleteModal.geofence.name }}</span>
            <Tag
              :color="getGeofenceTypeConfig(deleteModal.geofence.type).color"
              class="ml-2"
            >
              {{ getGeofenceTypeConfig(deleteModal.geofence.type).text }}
            </Tag>
          </div>
          <p class="text-sm text-gray-600">
            {{ deleteModal.geofence.description || '无描述' }}
          </p>
          <p class="mt-1 text-xs text-gray-500">
            创建时间: {{ formatTime(deleteModal.geofence.createTime) }}
          </p>
          <p
            v-if="
              deleteModal.geofence.droneIds &&
              deleteModal.geofence.droneIds.length > 0
            "
            class="mt-1 text-xs text-orange-600"
          >
            ⚠️ 此围栏关联了
            {{ deleteModal.geofence.droneIds.length }}
            台无人机，删除后将解除关联
          </p>
        </div>
      </div>
    </Modal>

    <!-- 详情查看抽屉 -->
    <Drawer
      v-model:open="detailModal.visible"
      placement="right"
      width="600"
      :closable="true"
      @close="closeDetailModal"
    >
      <template #title>
        <div class="flex items-center justify-between" style="width: 100%">
          <span>地理围栏 {{ detailModal.geofence?.name }}</span>
          <Space>
            <Button
              type="primary"
              size="small"
              @click="
                handleLocate(detailModal.geofence!);
                closeDetailModal();
              "
              :disabled="!detailModal.geofence"
            >
              <template #icon><EnvironmentOutlined /></template>
              地图定位
            </Button>
            <Button
              danger
              size="small"
              @click="
                showDeleteModal(detailModal.geofence!);
                closeDetailModal();
              "
              :disabled="!detailModal.geofence"
            >
              <template #icon><DeleteOutlined /></template>
              删除
            </Button>
          </Space>
        </div>
      </template>

      <template #extra>
        <Button type="default" @click="closeDetailModal"> 关闭 </Button>
      </template>

      <template v-if="detailModal.geofence">
        <!-- 基本信息 -->
        <Descriptions title="基本信息" bordered :column="1" class="mb-6">
          <Descriptions.Item label="围栏名称">
            {{ detailModal.geofence.name }}
          </Descriptions.Item>
          <Descriptions.Item label="围栏类型">
            <Tag
              :color="getGeofenceTypeConfig(detailModal.geofence.type).color"
              class="text-sm"
            >
              {{ getGeofenceTypeConfig(detailModal.geofence.type).icon }}
              {{ getGeofenceTypeConfig(detailModal.geofence.type).text }}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="围栏描述">
            {{ detailModal.geofence.description || '暂无描述' }}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {{ formatTime(detailModal.geofence.createTime) }}
          </Descriptions.Item>
          <Descriptions.Item label="围栏ID">
            <span class="font-mono text-sm">{{ detailModal.geofence.id }}</span>
          </Descriptions.Item>
        </Descriptions>

        <!-- 地理信息 -->
        <Descriptions title="地理信息" bordered :column="1" class="mb-6">
          <Descriptions.Item label="中心坐标">
            {{ getCoordinatesSummary(detailModal.geofence.coordinates) }}
          </Descriptions.Item>
          <Descriptions.Item label="顶点数量">
            {{ detailModal.geofence.coordinates.length }} 个
          </Descriptions.Item>
          <Descriptions.Item label="关联无人机">
            {{ detailModal.geofence.droneIds?.length || 0 }} 台
          </Descriptions.Item>
        </Descriptions>

        <!-- 围栏形状预览 -->
        <div class="mb-6">
          <h3 class="mb-3 text-base font-medium">围栏形状</h3>
          <div class="flex justify-center">
            <div
              class="h-64 w-64 overflow-hidden rounded-lg border-2 shadow-sm"
              :style="{
                borderColor: getGeofenceTypeConfig(detailModal.geofence.type)
                  .color,
              }"
            >
              <div
                class="flex h-full w-full items-center justify-center"
                :style="{
                  backgroundColor: getGeofenceTypeConfig(
                    detailModal.geofence.type,
                  ).bgColor,
                }"
              >
                <div
                  v-html="generateLargeThumbnail(detailModal.geofence)"
                  class="h-full w-full"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 详细坐标 -->
        <div class="mb-6">
          <h3 class="mb-3 text-base font-medium">详细坐标</h3>
          <div class="max-h-48 overflow-y-auto rounded-lg bg-gray-50 p-4">
            <div class="space-y-2">
              <div
                v-for="(coord, index) in detailModal.geofence.coordinates"
                :key="index"
                class="flex justify-between border-b border-gray-200 py-1 text-sm last:border-0"
              >
                <span class="font-medium text-gray-600"
                  >点 {{ index + 1 }}:</span
                >
                <span class="font-mono text-gray-900">
                  {{ coord.lng.toFixed(6) }}, {{ coord.lat.toFixed(6) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 关联无人机信息 -->
        <div
          v-if="
            detailModal.geofence.droneIds &&
            detailModal.geofence.droneIds.length > 0
          "
          class="mb-6"
        >
          <h3 class="mb-3 text-base font-medium">关联无人机</h3>
          <div class="rounded-lg bg-blue-50 p-4">
            <div class="flex flex-wrap gap-2">
              <Tag
                v-for="droneId in detailModal.geofence.droneIds"
                :key="droneId"
                color="blue"
                class="mb-1"
              >
                🚁 {{ droneId }}
              </Tag>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="py-8 text-center text-gray-500">
          <InfoCircleOutlined class="mb-2 text-4xl" />
          <p>未选择地理围栏</p>
        </div>
      </template>
    </Drawer>
  </Card>
</template>

<style scoped>
/* 自定义样式 */
.shadow-md {
  box-shadow:
    0 4px 6px -1px rgb(0 0 0 / 10%),
    0 2px 4px -1px rgb(0 0 0 / 6%);
}

/* 文本截断样式 */
.line-clamp-2 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 自定义滚动条 */
.overflow-y-auto {
  scrollbar-color: #d1d5db #f3f4f6;
  scrollbar-width: thin;
}

.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: #f3f4f6;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background-color: #d1d5db;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background-color: #9ca3af;
}

/* 列表项悬停效果 */
:deep(.ant-list-item:hover) {
  background-color: #f9fafb;
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
