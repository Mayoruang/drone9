<script lang="ts" setup>
import { ref, onMounted, onUnmounted, watch, defineProps, defineEmits } from 'vue';
import type { PropType } from 'vue';

// 定义组件属性
const props = defineProps({
  // 中心点坐标
  center: {
    type: Object,
    default: () => ({ lng: 116.404, lat: 39.915 })
  },
  // 缩放级别
  zoom: {
    type: Number,
    default: 12
  },
  // 无人机数据
  drones: {
    type: Array as PropType<DroneData[]>,
    default: () => []
  },
  // 是否显示控件
  showControls: {
    type: Boolean,
    default: true
  },
  // 地图容器高度
  height: {
    type: String,
    default: '700px'
  },
  // API密钥
  apiKey: {
    type: String,
    default: 'PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE'
  }
});

// 定义事件
const emit = defineEmits(['map-ready', 'marker-click']);

// 地图实例
const mapInstance = ref<any>(null);
// 标记集合
const markers = ref<any[]>([]);
// 标记集合
const loading = ref(true);
// 错误信息
const error = ref('');

// 地图状态
const mapStatus = ref({
  initialized: false,
  apiLoaded: false
});

// 向窗口对象添加BMap接口以避免TypeScript错误
declare global {
  interface Window {
    BMap: any;
    BMap_Symbol_SHAPE_POINT: any;
    BMap_Symbol_SHAPE_PLANE: any;
    BMap_Symbol_SHAPE_WARNING: any;
    BMap_loadScriptTime: number;
    initBaiduMap: () => void;
  }
}

// 定义无人机数据接口
interface DroneData {
  droneId: string;
  serialNumber: string;
  model: string;
  status: string;
  batteryPercentage: number;
  position: {
    latitude: number;
    longitude: number;
    altitude: number;
  };
  speed: number;
  lastHeartbeat: string;
  [key: string]: any; // 允许其他属性
}

// 无人机状态对应的颜色
const statusColors = {
  FLYING: '#1890ff', // 蓝色 - 正常执行任务
  IDLE: '#52c41a',   // 绿色 - 地面待命
  LOW_BATTERY: '#faad14', // 黄色 - 低电量警告
  TRAJECTORY_ERROR: '#ff4d4f', // 红色 - 轨迹异常警告
  OFFLINE: '#d9d9d9' // 灰色 - 离线
};

// 加载百度地图API
const loadBaiduMapAPI = () => {
  if (document.querySelector('script[src*="api.map.baidu.com"]')) {
    console.log('百度地图API已加载，跳过加载过程');
    return;
  }

  // 注册全局回调函数
  window.initBaiduMap = () => {
    console.log('百度地图API加载完成');
    mapStatus.value.apiLoaded = true;
    initializeMap();
  };

  // 加载API脚本
  const script = document.createElement('script');
  script.src = `https://api.map.baidu.com/api?v=3.0&ak=${props.apiKey}&callback=initBaiduMap`;
  script.onerror = (e) => {
    error.value = '百度地图API加载失败';
    loading.value = false;
    console.error('百度地图API加载失败', e);
  };
  
  document.body.appendChild(script);
  console.log('百度地图API脚本已添加到DOM');
};

// 初始化地图
const initializeMap = () => {
  try {
    if (!window.BMap) {
      console.error('BMap API 未加载');
      error.value = 'BMap API 未加载';
      loading.value = false;
      return;
    }

    const container = document.getElementById('baiduMapContainer');
    if (!container) {
      console.error('地图容器不存在');
      error.value = '地图容器不存在';
      loading.value = false;
      return;
    }

    console.log('初始化地图, 容器尺寸:', container.offsetWidth, container.offsetHeight);
    
    // 创建地图实例
    const BMap = window.BMap;
    
    // 保存Symbol常量
    window.BMap_Symbol_SHAPE_POINT = BMap.Symbol.SHAPE_POINT;
    window.BMap_Symbol_SHAPE_PLANE = BMap.Symbol.SHAPE_PLANE;
    window.BMap_Symbol_SHAPE_WARNING = BMap.Symbol.SHAPE_WARNING;
    
    // 创建中心点
    const centerPoint = new BMap.Point(props.center.lng, props.center.lat);
    
    // 创建地图实例
    const map = new BMap.Map('baiduMapContainer');
    // 初始化地图，设置中心点和缩放级别
    map.centerAndZoom(centerPoint, props.zoom);
    
    // 添加控件
    if (props.showControls) {
      map.addControl(new BMap.NavigationControl());
      map.addControl(new BMap.ScaleControl());
      map.addControl(new BMap.OverviewMapControl());
    }
    
    // 允许鼠标滚轮缩放
    map.enableScrollWheelZoom(true);
    
    // 保存地图实例
    mapInstance.value = map;
    mapStatus.value.initialized = true;
    
    // 通知父组件地图已准备好
    emit('map-ready', map);
    
    // 更新标记
    renderMarkers();
    
    // 更新加载状态
    loading.value = false;
    
    console.log('地图初始化完成');
  } catch (err) {
    console.error('地图初始化失败', err);
    error.value = `地图初始化失败: ${err instanceof Error ? err.message : '未知错误'}`;
    loading.value = false;
  }
};

// 创建自定义图标
const createDroneIcon = (drone: DroneData) => {
  if (!window.BMap) return null;
  
  const status = drone.status || 'OFFLINE';
  const color = statusColors[status as keyof typeof statusColors] || statusColors.OFFLINE;
  
  // 根据状态选择合适的图标
  let symbolType;
  switch (status) {
    case 'FLYING':
      symbolType = window.BMap_Symbol_SHAPE_PLANE;
      break;
    case 'LOW_BATTERY':
    case 'TRAJECTORY_ERROR':
      symbolType = window.BMap_Symbol_SHAPE_WARNING;
      break;
    default:
      symbolType = window.BMap_Symbol_SHAPE_POINT;
  }
  
  // 创建图标样式
  return new window.BMap.Symbol(symbolType, {
    scale: status === 'FLYING' ? 0.5 : 1.2,
    fillColor: color,
    fillOpacity: 0.8,
    strokeColor: status === 'FLYING' ? color : '#ffffff',
    strokeOpacity: 1,
    strokeWeight: 1
  });
};

// 创建信息窗口
const createInfoWindow = (drone: DroneData) => {
  if (!window.BMap) return null;
  
  const status = drone.status || 'OFFLINE';
  const color = statusColors[status as keyof typeof statusColors] || statusColors.OFFLINE;
  const statusText = {
    FLYING: '飞行中',
    IDLE: '地面待命',
    LOW_BATTERY: '低电量警告',
    TRAJECTORY_ERROR: '轨迹异常警告',
    OFFLINE: '离线'
  }[status as keyof typeof statusColors] || '未知';
  
  const getBatteryColor = (percentage: number) => {
    if (percentage <= 20) return '#ff4d4f';
    if (percentage <= 40) return '#faad14';
    return '#52c41a';
  };
  
  // 使用卡片样式的信息窗口
  const content = `
    <div style="min-width: 220px; padding: 8px;">
      <div style="display: flex; align-items: center; margin-bottom: 8px;">
        <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${color}; margin-right: 6px;"></div>
        <h4 style="margin: 0; color: #1890ff; font-weight: 600;">${drone.serialNumber || 'Unknown'}</h4>
      </div>

      <div style="background-color: #f5f5f5; border-radius: 4px; padding: 8px; margin-bottom: 8px;">
        <p style="margin: 4px 0; display: flex; justify-content: space-between;">
          <span style="color: #666;">型号:</span> <span>${drone.model || 'Unknown'}</span>
        </p>
        <p style="margin: 4px 0; display: flex; justify-content: space-between;">
          <span style="color: #666;">状态:</span> <span style="color: ${color};">${statusText}</span>
        </p>
        <p style="margin: 4px 0; display: flex; justify-content: space-between;">
          <span style="color: #666;">电量:</span>
          <span>
            <div style="display: inline-block; width: 50px; height: 8px; background-color: #eee; border-radius: 4px; vertical-align: middle;">
              <div style="width: ${drone.batteryPercentage || 0}%; height: 100%; background-color: ${getBatteryColor(drone.batteryPercentage || 0)}; border-radius: 4px;"></div>
            </div>
            ${drone.batteryPercentage || 0}%
          </span>
        </p>
      </div>

      <div style="font-size: 12px; color: #666;">
        <p style="margin: 2px 0;">高度: ${drone.position?.altitude || 0}米</p>
        <p style="margin: 2px 0;">速度: ${drone.speed || 0}m/s</p>
        <p style="margin: 2px 0;">更新: ${new Date(drone.lastHeartbeat || Date.now()).toLocaleTimeString()}</p>
      </div>
    </div>
  `;

  return new window.BMap.InfoWindow(content, {
    width: 240,
    height: 200,
    enableCloseOnClick: false,
    enableAutoPan: true,
    title: ""
  });
};

// 渲染所有标记
const renderMarkers = () => {
  try {
    if (!mapInstance.value || !props.drones || !window.BMap) {
      console.error('无法渲染标记: 地图未初始化或无人机数据为空');
      return;
    }
    
    // 清除现有标记
    markers.value.forEach(marker => {
      if (mapInstance.value) {
        mapInstance.value.removeOverlay(marker);
      }
    });
    markers.value = [];
    
    // 添加新标记
    props.drones.forEach((drone: DroneData) => {
      try {
        if (!drone.position || !drone.position.longitude || !drone.position.latitude) {
          console.warn('无人机位置数据无效', drone);
          return;
        }
        
        const point = new window.BMap.Point(drone.position.longitude, drone.position.latitude);
        const icon = createDroneIcon(drone);
        
        if (!icon) {
          console.warn('无法创建无人机图标', drone);
          return;
        }
        
        const marker = new window.BMap.Marker(point, { icon });
        
        // 点击事件
        marker.addEventListener('click', () => {
          emit('marker-click', drone);
        });
        
        // 悬停事件
        marker.addEventListener('mouseover', () => {
          const infoWindow = createInfoWindow(drone);
          marker.openInfoWindow(infoWindow);
        });
        
        // 添加到地图
        if (mapInstance.value) {
          mapInstance.value.addOverlay(marker);
          markers.value.push(marker);
        }
      } catch (err) {
        console.error('添加标记时出错', err, drone);
      }
    });
    
    console.log(`已渲染 ${markers.value.length} 个标记`);
  } catch (err) {
    console.error('渲染标记时出错', err);
  }
};

// 组件挂载后加载地图
onMounted(() => {
  console.log('BaiduMap 组件已挂载');
  // 设置加载中状态
  loading.value = true;
  error.value = '';
  
  // 延长等待时间，确保DOM已完全渲染
  setTimeout(() => {
    // 先创建容器元素
    const container = document.getElementById('baiduMapContainer');
    if (!container) {
      console.warn('地图容器不存在，可能DOM尚未完全加载，重试中...');
      // 再次延迟尝试
      setTimeout(() => {
        loadBaiduMapAPI();
      }, 1000);
    } else {
      console.log('地图容器已找到，尺寸:', container.offsetWidth, container.offsetHeight);
      loadBaiduMapAPI();
    }
  }, 1000);
});

// 监听无人机数据变化
watch(() => props.drones, () => {
  if (mapStatus.value.initialized) {
    console.log('无人机数据已更新，重新渲染标记');
    renderMarkers();
  }
}, { deep: true });

// 组件卸载时清理
onUnmounted(() => {
  console.log('BaiduMap 组件已卸载');
  // 清除标记
  markers.value.forEach(marker => {
    if (mapInstance.value) {
      mapInstance.value.removeOverlay(marker);
    }
  });
  markers.value = [];
  
  // 重置地图实例
  mapInstance.value = null;
});
</script>

<template>
  <div class="baidu-map-wrapper" style="position: relative;">
    <!-- 地图容器 -->
    <div 
      id="baiduMapContainer" 
      class="baidu-map-container"
      :style="{ height: height, width: '100%' }"
    ></div>
    
    <!-- 加载指示器 -->
    <div v-if="loading" class="map-loading-overlay">
      <div class="spinner"></div>
      <p>加载地图中...</p>
    </div>
    
    <!-- 错误提示 -->
    <div v-if="error" class="map-error-overlay">
      <div class="error-icon">!</div>
      <p>{{ error }}</p>
      <button @click="loadBaiduMapAPI">重试</button>
    </div>
    
    <!-- 插槽内容 -->
    <slot></slot>
  </div>
</template>

<style scoped>
.baidu-map-wrapper {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 4px;
}

.baidu-map-container {
  width: 100%;
  min-height: 400px;
  background-color: #f0f2f5;
}

.map-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.map-error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #1890ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 10px;
}

.error-icon {
  width: 50px;
  height: 50px;
  line-height: 50px;
  text-align: center;
  font-size: 30px;
  font-weight: bold;
  color: white;
  background-color: #ff4d4f;
  border-radius: 50%;
  margin-bottom: 10px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style> 