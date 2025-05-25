<script lang="ts" setup>
import type { PropType } from 'vue';

import {
  defineEmits,
  defineProps,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from 'vue';

// 地理围栏数据接口
interface GeofenceData {
  id: string;
  name: string;
  type: 'FLY_ZONE' | 'NO_FLY_ZONE' | 'RESTRICTED_ZONE';
  coordinates: Array<{ lat: number; lng: number }>;
  description?: string;
  createTime: string;
  thumbnail?: string;
  droneIds?: string[];
}

// 定义组件属性
const props = defineProps({
  // 地理围栏列表
  geofences: {
    type: Array as PropType<GeofenceData[]>,
    default: () => [],
  },
  // 是否处于绘制模式
  isDrawing: {
    type: Boolean,
    default: false,
  },
  // 地图中心点（沈阳）
  center: {
    type: Object,
    default: () => ({ lng: 123.429, lat: 41.796 }),
  },
  // 缩放级别
  zoom: {
    type: Number,
    default: 11,
  },
  // API密钥
  apiKey: {
    type: String,
    default: 'PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE',
  },
});

// 定义事件
const emit = defineEmits(['draw-complete', 'geofence-click']);

// 状态管理
const mapInstance = ref<any>(null);
const loading = ref(true);
const error = ref('');
const currentPolygon = ref<any>(null);
const drawingPoints = ref<any[]>([]);
const drawingMarkers = ref<any[]>([]);

// 地图状态
const mapStatus = ref({
  initialized: false,
  apiLoaded: false,
});

// 存储地图上的围栏覆盖物
const geofenceOverlays = ref<Map<string, any>>(new Map());

// 向窗口对象添加BMap接口
declare global {
  interface Window {
    BMap: any;
    BMapGL: any;
    initGeofenceMap: () => void;
  }
}

// 围栏类型对应的样式
const geofenceStyles = {
  NO_FLY_ZONE: {
    strokeColor: '#ff4d4f',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#ff4d4f',
    fillOpacity: 0.2,
  },
  FLY_ZONE: {
    strokeColor: '#52c41a',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#52c41a',
    fillOpacity: 0.2,
  },
  RESTRICTED_ZONE: {
    strokeColor: '#faad14',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#faad14',
    fillOpacity: 0.2,
  },
};

// 加载百度地图API
const loadBaiduMapAPI = () => {
  if (document.querySelector('script[src*="api.map.baidu.com"]')) {
    console.log('百度地图API已加载');
    if (window.BMap) {
      mapStatus.value.apiLoaded = true;
      initializeMap();
    }
    return;
  }

  // 注册全局回调函数
  window.initGeofenceMap = () => {
    console.log('地理围栏地图API加载完成');
    mapStatus.value.apiLoaded = true;
    initializeMap();
  };

  // 加载API脚本
  const script = document.createElement('script');
  script.src = `https://api.map.baidu.com/api?v=3.0&ak=${props.apiKey}&callback=initGeofenceMap`;
  script.onerror = (e) => {
    error.value = '百度地图API加载失败';
    loading.value = false;
    console.error('百度地图API加载失败', e);
  };

  document.body.append(script);
};

// 初始化地图
const initializeMap = () => {
  try {
    if (!window.BMap) {
      error.value = 'BMap API 未加载';
      loading.value = false;
      return;
    }

    const container = document.querySelector('#geofenceMapContainer');
    if (!container) {
      error.value = '地图容器不存在';
      loading.value = false;
      return;
    }

    const BMap = window.BMap;

    // 创建中心点
    const centerPoint = new BMap.Point(props.center.lng, props.center.lat);

    // 创建地图实例
    const map = new BMap.Map('geofenceMapContainer');
    map.centerAndZoom(centerPoint, props.zoom);

    // 添加控件
    map.addControl(new BMap.NavigationControl());
    map.addControl(new BMap.ScaleControl());

    // 允许鼠标滚轮缩放
    map.enableScrollWheelZoom(true);

    // 保存地图实例
    mapInstance.value = map;
    mapStatus.value.initialized = true;

    // 渲染现有围栏
    renderGeofences();

    loading.value = false;

    console.log('地理围栏地图初始化完成');
  } catch (error_) {
    console.error('地图初始化失败', error_);
    error.value = `地图初始化失败: ${error_ instanceof Error ? error_.message : '未知错误'}`;
    loading.value = false;
  }
};

// 渲染地理围栏
const renderGeofences = () => {
  if (!mapInstance.value || !window.BMap) return;

  const BMap = window.BMap;

  // 清除现有覆盖物
  geofenceOverlays.value.forEach((overlay) => {
    mapInstance.value.removeOverlay(overlay);
  });
  geofenceOverlays.value.clear();

  // 添加新的围栏
  props.geofences.forEach((geofence) => {
    const points = geofence.coordinates.map(
      (coord) => new BMap.Point(coord.lng, coord.lat),
    );

    const polygon = new BMap.Polygon(points, geofenceStyles[geofence.type]);

    // 添加点击事件
    polygon.addEventListener('click', () => {
      emit('geofence-click', geofence);
    });

    // 添加信息窗口
    const infoWindow = new BMap.InfoWindow(`
      <div style="padding: 8px;">
        <h4 style="margin: 0 0 8px 0; color: ${geofence.type === 'NO_FLY_ZONE' ? '#ff4d4f' : geofence.type === 'RESTRICTED_ZONE' ? '#ffa500' : '#52c41a'};">
          ${geofence.name}
        </h4>
        <p style="margin: 0; font-size: 12px; color: #666;">
          类型: ${geofence.type === 'NO_FLY_ZONE' ? '禁飞区' : geofence.type === 'RESTRICTED_ZONE' ? '限制区' : '允飞区'}
        </p>
        <p style="margin: 4px 0 0 0; font-size: 12px; color: #666;">
          ${geofence.description || '无描述'}
        </p>
      </div>
    `);

    polygon.addEventListener('click', (e: any) => {
      mapInstance.value.openInfoWindow(infoWindow, e.point);
    });

    mapInstance.value.addOverlay(polygon);
    geofenceOverlays.value.set(geofence.id, polygon);
  });
};

// 开始绘制多边形
const startDrawPolygon = () => {
  if (!mapInstance.value || !window.BMap) return;

  // 清除之前的绘制
  clearCurrentDrawing();

  // 重置绘制点数组和标记数组
  drawingPoints.value = [];
  drawingMarkers.value = [];

  // 添加地图点击事件
  mapInstance.value.addEventListener('click', handleMapClick);
  mapInstance.value.addEventListener('dblclick', handleMapDoubleClick);

  // 设置鼠标样式
  mapInstance.value.setDefaultCursor('crosshair');

  console.log('开始绘制多边形，点击添加点，双击完成绘制');
};

// 处理地图点击
const handleMapClick = (e: any) => {
  if (!props.isDrawing || !window.BMap) return;

  const BMap = window.BMap;
  const point = e.point;

  // 添加点
  drawingPoints.value.push(point);

  // 添加标记点并跟踪
  const marker = new BMap.Marker(point);
  mapInstance.value.addOverlay(marker);
  drawingMarkers.value.push(marker); // 跟踪所有绘制标记

  // 如果有足够的点，绘制多边形预览
  if (drawingPoints.value.length >= 3) {
    // 创建多边形预览（透明度较低，表示正在绘制）
    const polygon = new BMap.Polygon(drawingPoints.value, {
      strokeColor: '#1890ff',
      strokeWeight: 2,
      strokeOpacity: 0.8,
      fillColor: '#1890ff',
      fillOpacity: 0.1,
    });

    // 移除之前的预览
    if (currentPolygon.value) {
      mapInstance.value.removeOverlay(currentPolygon.value);
    }

    mapInstance.value.addOverlay(polygon);
    currentPolygon.value = polygon;
  } else if (drawingPoints.value.length === 2) {
    // 只有两个点时，显示线段
    const polyline = new BMap.Polyline(drawingPoints.value, {
      strokeColor: '#1890ff',
      strokeWeight: 2,
      strokeOpacity: 0.8,
    });

    // 移除之前的线条
    if (currentPolygon.value) {
      mapInstance.value.removeOverlay(currentPolygon.value);
    }

    mapInstance.value.addOverlay(polyline);
    currentPolygon.value = polyline;
  }

  console.log(
    `添加点 ${drawingPoints.value.length}: ${point.lng}, ${point.lat}`,
  );
};

// 处理地图双击
const handleMapDoubleClick = (e: any) => {
  if (!props.isDrawing) return;

  // 阻止默认行为
  e.preventDefault && e.preventDefault();

  finishDrawing();
};

// 完成绘制
const finishDrawing = () => {
  if (drawingPoints.value.length < 3) {
    console.warn('多边形至少需要3个点');
    return;
  }

  // 转换为坐标格式
  const coordinates = drawingPoints.value.map((point) => ({
    lng: point.lng,
    lat: point.lat,
  }));

  // 清理当前绘制内容（包括预览多边形）
  clearCurrentDrawing();

  // 清理绘制状态
  clearDrawingState();

  // 发送绘制完成事件
  emit('draw-complete', coordinates);

  console.log('绘制完成，坐标数:', coordinates.length);
};

// 取消绘制
const cancelDraw = () => {
  clearCurrentDrawing();
  clearDrawingState();
};

// 清除当前绘制
const clearCurrentDrawing = () => {
  if (!mapInstance.value) return;

  // 移除所有绘制过程中的标记点
  drawingMarkers.value.forEach((marker) => {
    mapInstance.value.removeOverlay(marker);
  });
  drawingMarkers.value = [];

  // 移除当前的多边形或线条预览
  if (currentPolygon.value) {
    mapInstance.value.removeOverlay(currentPolygon.value);
    currentPolygon.value = null;
  }

  // 清空绘制点数组
  drawingPoints.value = [];

  console.log('已清除所有绘制内容');
};

// 清除绘制状态
const clearDrawingState = () => {
  if (!mapInstance.value) return;

  // 移除事件监听器
  mapInstance.value.removeEventListener('click', handleMapClick);
  mapInstance.value.removeEventListener('dblclick', handleMapDoubleClick);

  // 恢复鼠标样式
  mapInstance.value.setDefaultCursor('default');

  // 清理绘制相关的状态
  drawingPoints.value = [];
  drawingMarkers.value = [];
  currentPolygon.value = null;
};

// 添加地理围栏
const addGeofence = (geofence: GeofenceData) => {
  if (!mapInstance.value || !window.BMap) return;

  const BMap = window.BMap;
  const points = geofence.coordinates.map(
    (coord) => new BMap.Point(coord.lng, coord.lat),
  );

  const polygon = new BMap.Polygon(points, geofenceStyles[geofence.type]);

  // 添加点击事件
  polygon.addEventListener('click', () => {
    emit('geofence-click', geofence);
  });

  mapInstance.value.addOverlay(polygon);
  geofenceOverlays.value.set(geofence.id, polygon);

  // 聚焦到新围栏
  focusGeofence(geofence);
};

// 移除地理围栏
const removeGeofence = (id: string) => {
  const overlay = geofenceOverlays.value.get(id);
  if (overlay && mapInstance.value) {
    mapInstance.value.removeOverlay(overlay);
    geofenceOverlays.value.delete(id);
  }
};

// 聚焦到地理围栏
const focusGeofence = (geofence: GeofenceData) => {
  if (!mapInstance.value || !window.BMap) return;

  const BMap = window.BMap;
  const points = geofence.coordinates.map(
    (coord) => new BMap.Point(coord.lng, coord.lat),
  );

  // 创建边界
  const viewport = mapInstance.value.getViewport(points);
  mapInstance.value.centerAndZoom(viewport.center, viewport.zoom);
};

// 监听地理围栏数据变化
watch(
  () => props.geofences,
  () => {
    renderGeofences();
  },
  { deep: true },
);

// 键盘事件处理
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.isDrawing) {
    console.log(
      `按下ESC键，取消绘制，清除已绘制的${drawingPoints.value.length}个点`,
    );
    cancelDraw();
    event.preventDefault(); // 阻止默认行为
  }
  if (
    event.key === 'Enter' &&
    props.isDrawing &&
    drawingPoints.value.length >= 3
  ) {
    console.log(`按下Enter键，完成绘制，共${drawingPoints.value.length}个点`);
    finishDrawing();
    event.preventDefault(); // 阻止默认行为
  }
};

// 强制清理所有绘制内容（用于表单取消时的彻底清理）
const forceClearAll = () => {
  if (!mapInstance.value) return;

  // 清理当前绘制内容
  clearCurrentDrawing();

  // 清理绘制状态
  clearDrawingState();

  // 额外保险：再次检查并清理可能的残留覆盖物
  if (currentPolygon.value) {
    try {
      mapInstance.value.removeOverlay(currentPolygon.value);
    } catch (e) {
      console.warn('清理预览多边形时出错:', e);
    }
    currentPolygon.value = null;
  }

  // 清理所有绘制标记
  drawingMarkers.value.forEach((marker) => {
    try {
      mapInstance.value.removeOverlay(marker);
    } catch (e) {
      console.warn('清理绘制标记时出错:', e);
    }
  });
  drawingMarkers.value = [];

  console.log('强制清理所有绘制内容完成');
};

// 暴露方法给父组件
defineExpose({
  startDrawPolygon,
  cancelDraw,
  addGeofence,
  removeGeofence,
  focusGeofence,
  forceClearAll,
});

onMounted(() => {
  loadBaiduMapAPI();
  document.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown);
  if (mapInstance.value) {
    clearDrawingState();
  }
});
</script>

<template>
  <div class="relative h-full w-full">
    <!-- 地图容器 -->
    <div
      id="geofenceMapContainer"
      class="geofence-map-container h-full w-full"
      :class="{ 'cursor-crosshair': isDrawing }"
    ></div>

    <!-- 加载状态 -->
    <div
      v-if="loading"
      class="absolute inset-0 flex items-center justify-center bg-gray-100"
    >
      <div class="text-center">
        <div
          class="mx-auto mb-2 h-8 w-8 animate-spin rounded-full border-b-2 border-blue-500"
        ></div>
        <p class="text-gray-600">加载地图中...</p>
      </div>
    </div>

    <!-- 错误状态 -->
    <div
      v-if="error"
      class="absolute inset-0 flex items-center justify-center bg-gray-100"
    >
      <div class="text-center text-red-500">
        <p class="text-lg font-medium">地图加载失败</p>
        <p class="mt-1 text-sm">{{ error }}</p>
      </div>
    </div>

    <!-- 绘制提示 -->
    <div
      v-if="isDrawing"
      class="absolute left-4 top-4 z-10 rounded-lg bg-blue-500 px-4 py-2 text-white shadow-lg"
    >
      <div class="flex items-center space-x-2">
        <div class="h-2 w-2 animate-pulse rounded-full bg-white"></div>
        <span class="text-sm font-medium">绘制模式</span>
      </div>
      <p class="mt-1 text-xs opacity-90">
        点击添加点 | 双击或Enter完成 | ESC取消
      </p>
      <p class="text-xs opacity-75">已添加 {{ drawingPoints.length }} 个点</p>
    </div>

    <!-- 地图工具提示 -->
    <div
      class="absolute bottom-4 left-4 z-10 rounded-lg bg-white bg-opacity-90 px-3 py-2 text-xs text-gray-600 shadow-md"
    >
      <p>• 点击围栏查看详情</p>
      <p>• 滚轮缩放地图</p>
      <p>• 拖拽移动地图</p>
    </div>
  </div>
</template>

<style scoped>
/* 动画样式 */
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.cursor-crosshair {
  cursor: crosshair !important;
}

/* 确保地图容器有合适的z-index */
.geofence-map-container {
  z-index: 1;
}

.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.animate-spin {
  animation: spin 1s linear infinite;
}
</style>
