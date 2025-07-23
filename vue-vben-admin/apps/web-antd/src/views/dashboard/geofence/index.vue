<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';

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

import {
  getAllGeofences,
  createGeofence,
  deleteGeofence as deleteGeofenceApi,
  testGeofenceAPI,
  bindDronesToGeofence,
  type GeofenceData
} from '#/api/geofence';
import { requestClient, baseRequestClient } from '#/api/request';

import GeofenceList from './components/GeofenceList.vue';
import GeofenceMap from './components/GeofenceMap.vue';

// 扩展地理围栏数据类型以包含额外字段
interface ExtendedGeofenceData extends GeofenceData {
  altitudeMin?: number;
  altitudeMax?: number;
  priority?: number;
  areaSquareMeters?: number;
  droneCount?: number; // 关联的无人机数量（只对RESTRICTED_ZONE有效）
  center: [number, number];
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
const apiConnected = ref(false);

// 表单数据
const geofenceForm = reactive({
  name: '',
  type: 'NO_FLY_ZONE' as 'FLY_ZONE' | 'NO_FLY_ZONE' | 'RESTRICTED_ZONE',
  description: '',
  droneIds: [] as string[],
  altitudeMin: undefined as number | undefined,
  altitudeMax: undefined as number | undefined,
});

// 地理围栏列表 - 现在从API获取
const geofenceList = ref<ExtendedGeofenceData[]>([]);

// Mock数据 - 无人机列表（沈阳地区的无人机数据作为备选方案）
const droneList = ref<DroneData[]>([
  { id: '27ab5670-4643-4b87-8dd3-69de10785b65', serialNumber: 'SY-DJI-001', name: '大疆 Mavic Air 2 (沈阳故宫)' },
  { id: '4e46038f-e3aa-4d49-ac46-e516d3948adf', serialNumber: 'SY-DJI-002', name: '大疆 Mini 3 Pro (沈阳工业大学)' },
  { id: '995643aa-6926-4296-98c1-d3295c249be0', serialNumber: 'SY-AUTEL-001', name: 'Autel EVO II Pro (沈阳北站)' },
  { id: '14ca5bc3-69ac-4571-a52b-eff4ba072970', serialNumber: 'SY-YUNEEC-001', name: 'Yuneec Typhoon H Plus (沈阳农业大学)' },
  { id: '262f9dab-200f-406b-9031-f96fce463eb3', serialNumber: 'SY-DJI-003', name: '大疆 Phantom 4 RTK (沈阳奥体中心)' },
  { id: '6fde6608-832b-4f46-9475-c588c6dd220e', serialNumber: 'SY-DJI-004', name: '大疆 Matrice 300 RTK (沈阳桃仙机场)' },
]);

// 加载无人机列表
const loadDroneList = async () => {
  try {
    console.log('尝试从API加载无人机列表...');
    // 尝试从无人机API获取数据，使用分页查询
    const response = await baseRequestClient.get('/v1/drones', {
      params: {
        page: 0,
        size: 100,
      }
    });

    console.log('无人机API响应:', response);

    // 处理分页响应数据
    if (response.data && response.data.content && Array.isArray(response.data.content)) {
      droneList.value = response.data.content.map((drone: any) => ({
        id: drone.droneId || drone.id,
        serialNumber: drone.serialNumber,
        name: `${drone.model || '未知型号'} (${drone.serialNumber})`,
      }));
      console.log('成功从API加载无人机列表:', droneList.value.length);
    } else if (response.data && Array.isArray(response.data)) {
      // 如果不是分页响应，直接处理数组
      droneList.value = response.data.map((drone: any) => ({
        id: drone.droneId || drone.id,
        serialNumber: drone.serialNumber,
        name: `${drone.model || '未知型号'} (${drone.serialNumber})`,
      }));
      console.log('成功从API加载无人机列表(非分页):', droneList.value.length);
    } else {
      console.log('API返回的无人机数据格式不正确，使用沈阳地区Mock数据');
    }
  } catch (error) {
    console.warn('无法从API加载无人机列表，使用沈阳地区Mock数据:', error);
    // 保留沈阳地区Mock数据作为备选方案
  }
};

// 从API加载地理围栏数据
const loadGeofences = async () => {
  try {
    loading.value = true;

    // 使用baseRequestClient获取完整响应
    const response = await baseRequestClient.get('/v1/geofences', {
      params: {
        page: 0,
        size: 1000,
      }
    });

    console.log('API Response:', response);

    // 检查响应格式 - baseRequestClient返回完整的axios响应
    if (response.data && response.data.content && Array.isArray(response.data.content)) {
      // 转换数据格式
      geofenceList.value = response.data.content.map((item: any) => {
        // 从geometry中提取实际的多边形坐标
        let coordinates: Array<{ lat: number; lng: number }> = [];

        if (item.geometry && item.geometry.type === 'Polygon' && item.geometry.coordinates) {
          // GeoJSON Polygon格式: [[[lng, lat], [lng, lat], ...]]
          const ringCoordinates = item.geometry.coordinates[0]; // 外环坐标
          coordinates = ringCoordinates.map((coord: [number, number]) => ({
            lng: coord[0], // 经度
            lat: coord[1], // 纬度
          }));
        } else if (item.center && Array.isArray(item.center) && item.center.length === 2) {
          // 备用方案：如果没有geometry，从center点生成基本的方形坐标
          const [lng, lat] = item.center;
          const offset = 0.002; // 约200米的偏移
          coordinates = [
            { lat: lat + offset, lng: lng - offset },
            { lat: lat + offset, lng: lng + offset },
            { lat: lat - offset, lng: lng + offset },
            { lat: lat - offset, lng: lng - offset },
          ];
        }

        return {
          id: item.geofenceId || item.id,
          name: item.name,
          type: item.geofenceType || 'NO_FLY_ZONE', // 使用后端返回的实际类型
          coordinates, // 使用实际的多边形坐标
          description: item.description,
          createTime: item.createdAt || item.createTime,
          thumbnail: item.thumbnailUrl || item.thumbnail,
          droneIds: [], // 保留droneIds字段为空数组，用于兼容性
          active: item.active,
          // 新增字段
          altitudeMin: item.altitudeMin,
          altitudeMax: item.altitudeMax,
          priority: item.priority,
          areaSquareMeters: item.areaSquareMeters,
          droneCount: item.droneCount || 0, // 使用后端返回的实际无人机数量
          // 添加center字段 - 使用后端提供的center或计算得出
          center: (() => {
            // 优先使用后端提供的center
            if (item.center && Array.isArray(item.center) && item.center.length === 2) {
              return [item.center[0], item.center[1]] as [number, number];
            }
            // 如果后端没有提供center，从coordinates计算
            if (coordinates.length > 0) {
              const avgLng = coordinates.reduce((sum, coord) => sum + coord.lng, 0) / coordinates.length;
              const avgLat = coordinates.reduce((sum, coord) => sum + coord.lat, 0) / coordinates.length;
              return [avgLng, avgLat] as [number, number];
            }
            // 默认值
            return [0, 0] as [number, number];
          })(),
        };
      });

      console.log('Loaded geofences with actual geometry:', geofenceList.value);

      notification.success({
        message: '加载成功',
        description: `成功加载 ${geofenceList.value.length} 个地理围栏`,
      });
    } else {
      // 如果没有数据，设置为空数组
      geofenceList.value = [];
      console.log('No geofences found or unexpected response format');
    }

  } catch (error: any) {
    console.error('Failed to load geofences:', error);

    // 设置为空数组，避免页面崩溃
    geofenceList.value = [];

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
    loading.value = false;
  }
};

// 测试API连接
const testApiConnection = async () => {
  try {
    const response = await baseRequestClient.get('/v1/geofences/test');
    const isConnected = response.data && typeof response.data === 'string' && response.data.includes('working');
    apiConnected.value = isConnected;

    if (isConnected) {
      notification.success({
        message: 'API连接成功',
        description: '地理围栏服务连接正常',
      });
    } else {
      notification.warning({
        message: 'API连接异常',
        description: '地理围栏服务连接不稳定，部分功能可能受影响',
      });
    }
  } catch (error) {
    console.error('API connection test failed:', error);
    apiConnected.value = false;
    notification.error({
      message: 'API连接失败',
      description: '无法连接到地理围栏服务，请检查服务状态',
    });
  }
};

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
    altitudeMin: undefined,
    altitudeMax: undefined,
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

// 保存地理围栏 - 使用真实API
const saveGeofence = async () => {
  console.log('🎯 开始保存地理围栏...');
  console.log('表单数据:', geofenceForm);
  console.log('绘制数据:', editingGeofence.value);

  // 基本验证
  if (!geofenceForm.name.trim()) {
    notification.error({
      message: '请输入地理围栏名称',
    });
    console.log('❌ 验证失败：围栏名称为空');
    return;
  }

  if (!editingGeofence.value) {
    notification.error({
      message: '绘制数据丢失，请重新绘制',
    });
    console.log('❌ 验证失败：绘制数据丢失');
    return;
  }

  // 验证限制区必须关联无人机
  if (geofenceForm.type === 'RESTRICTED_ZONE' &&
      (!geofenceForm.droneIds || geofenceForm.droneIds.length === 0)) {
    notification.error({
      message: '限制区必须关联无人机',
      description: '请选择至少一个有权限在此限制区飞行的无人机',
    });
    console.log('❌ 验证失败：限制区未关联无人机');
    return;
  }

  // 设置加载状态
  loading.value = true;
  console.log('⏳ 设置loading状态为true');

  try {
    // 根据地理围栏类型决定是否包含droneIds
    const createData = {
      name: geofenceForm.name,
      type: geofenceForm.type,
      coordinates: editingGeofence.value.coordinates,
      description: geofenceForm.description,
      altitudeMin: geofenceForm.altitudeMin,
      altitudeMax: geofenceForm.altitudeMax,
      // 只有限制区才包含droneIds
      ...(geofenceForm.type === 'RESTRICTED_ZONE' && { droneIds: geofenceForm.droneIds })
    };

    console.log('📤 发送创建请求，数据:', createData);
    const response = await createGeofence(createData);
    console.log('📥 收到创建响应:', response);

    // 检查响应是否成功
    if (response && response.success) {
      console.log('✅ 地理围栏创建成功');
      const geofenceId = response.geofenceId;

      // 第二步：如果有选择的无人机，进行绑定操作
      if (geofenceForm.droneIds && geofenceForm.droneIds.length > 0 && geofenceId) {
        try {
          console.log('🔗 开始绑定无人机:', geofenceForm.droneIds);
          const bindResponse = await bindDronesToGeofence(geofenceId, geofenceForm.droneIds);

          if (bindResponse.success) {
            console.log('✅ 无人机绑定成功');
          } else {
            console.warn('⚠️ 无人机绑定失败:', bindResponse.message);
            notification.warning({
              message: '地理围栏创建成功，但无人机绑定失败',
              description: bindResponse.message || '部分功能可能不可用',
            });
          }
        } catch (bindError: any) {
          console.error('❌ 无人机绑定异常:', bindError);

          let bindErrorMessage = '无人机绑定失败，可以稍后在详情页面手动绑定无人机';

          if (bindError?.response) {
            if (bindError.response.status === 404) {
              bindErrorMessage = '选择的无人机不存在，请检查无人机列表';
            } else if (bindError.response.status === 400) {
              bindErrorMessage = bindError.response.data?.message || '无人机绑定参数错误';
            } else if (bindError.response.status === 403) {
              bindErrorMessage = '没有权限绑定无人机到地理围栏';
            }
          }

          notification.warning({
            message: '地理围栏创建成功，但无人机绑定失败',
            description: bindErrorMessage,
          });
        }
      }

      // 显示成功通知
      notification.success({
        message: '地理围栏创建成功',
        description: `${geofenceForm.name} 已添加到系统`,
      });

      // 立即重置状态和关闭模态框
      console.log('🔄 重置状态并关闭模态框');

      // 重置表单数据
      Object.assign(geofenceForm, {
        name: '',
        type: 'NO_FLY_ZONE',
        description: '',
        droneIds: [],
        altitudeMin: undefined,
        altitudeMax: undefined,
      });

      // 清除编辑状态
      editingGeofence.value = null;

      // 关闭模态框
      showGeofenceModal.value = false;
      console.log('✅ 模态框已关闭，状态已重置');

      // 构建新的地理围栏对象并立即添加到列表和地图
      const newGeofence = {
        id: response.geofenceId || '', // 确保有默认值
        name: createData.name,
        type: createData.type,
        coordinates: createData.coordinates,
        description: createData.description,
        createTime: new Date().toISOString(),
        thumbnail: undefined, // 缩略图将稍后异步生成
        droneIds: geofenceForm.droneIds || [],
        active: true,
        altitudeMin: createData.altitudeMin,
        altitudeMax: createData.altitudeMax,
        priority: 1,
        areaSquareMeters: undefined,
        droneCount: 0, // 使用后端返回的实际无人机数量
        // 计算临时中心点（后端重新加载时会更新为准确值）
        center: (() => {
          const coords = createData.coordinates;
          if (coords.length === 0) return [0, 0] as [number, number];
          const avgLng = coords.reduce((sum, coord) => sum + coord.lng, 0) / coords.length;
          const avgLat = coords.reduce((sum, coord) => sum + coord.lat, 0) / coords.length;
          return [avgLng, avgLat] as [number, number];
        })(),
      };

      // 立即添加到列表
      geofenceList.value.push(newGeofence as ExtendedGeofenceData);
      console.log('📋 已添加到地理围栏列表');

      // 立即在地图上显示新围栏（只有在有有效ID时才添加）
      if (mapRef.value && mapRef.value.addGeofence && response.geofenceId) {
        try {
          mapRef.value.addGeofence(newGeofence);
          console.log('🗺️ 已添加到地图');
        } catch (error) {
          console.warn('⚠️ 添加到地图失败:', error);
        }
      }

      // 异步重新加载完整的地理围栏列表以获得最新数据（包括缩略图等）
      setTimeout(async () => {
        try {
          await loadGeofences();
          console.log('🔄 已刷新地理围栏列表');

          // 刷新成功后，再次聚焦到新创建的地理围栏
          const updatedGeofence = geofenceList.value.find(g => g.id === response.geofenceId);
          if (updatedGeofence && mapRef.value && mapRef.value.focusGeofence) {
            mapRef.value.focusGeofence(updatedGeofence);
            console.log('🎯 已聚焦到新围栏');
          }
        } catch (error) {
          console.warn('⚠️ 刷新围栏列表失败:', error);
          // 即使刷新失败，新围栏也已经显示了，不影响用户体验
        }
      }, 2000); // 延长到2秒，给缩略图生成更多时间

    } else {
      // 处理创建失败的情况
      console.log('❌ 地理围栏创建失败');
      const errorMessage = response?.message || '创建地理围栏时发生未知错误';
      notification.error({
        message: '创建失败',
        description: errorMessage,
      });
      // 创建失败时不关闭模态框，让用户可以修改后重试
    }
  } catch (error: any) {
    console.error('❌ 创建地理围栏异常:', error);

    // 提取错误信息
    let errorMessage = '无法创建地理围栏，请检查网络连接或稍后重试';

    if (error?.response) {
      // 服务器返回了错误响应
      if (error.response.status === 403) {
        errorMessage = '权限不足，无法创建地理围栏';
      } else if (error.response.status === 400) {
        errorMessage = error.response.data?.message || '请求参数有误';
      } else if (error.response.status >= 500) {
        errorMessage = '服务器内部错误，请稍后重试';
      }
    } else if (error?.request) {
      // 网络错误
      errorMessage = '网络连接失败，请检查网络连接';
    }

    notification.error({
      message: '创建失败',
      description: errorMessage,
    });
    // 发生异常时不关闭模态框，让用户可以重试
  } finally {
    // 无论成功失败都要重置loading状态
    loading.value = false;
    console.log('⏳ 重置loading状态为false');
  }
};

// 取消地理围栏设置
const cancelGeofenceForm = () => {
  console.log('🚫 取消地理围栏设置...');

  // 重置表单数据
  Object.assign(geofenceForm, {
    name: '',
    type: 'NO_FLY_ZONE',
    description: '',
    droneIds: [],
    altitudeMin: undefined,
    altitudeMax: undefined,
  });
  console.log('🔄 表单数据已重置');

  // 清除临时存储的围栏数据
  editingGeofence.value = null;
  console.log('🗑️ 编辑数据已清除');

  // 关闭模态框
  showGeofenceModal.value = false;
  console.log('❌ 模态框已关闭');

  // 使用强力清理方法确保完全清除所有绘制内容
  if (mapRef.value?.forceClearAll) {
    mapRef.value.forceClearAll();
    console.log('🗺️ 地图绘制内容已清除');
  }

  // 重置绘制状态
  isDrawing.value = false;
  console.log('✅ 绘制状态已重置');

  notification.info({
    message: '已取消围栏设置',
    description: '绘制的围栏已撤销，所有数据已清除',
  });
};

// 删除地理围栏 - 使用真实API
const deleteGeofence = async (id: string) => {
  try {
    loading.value = true;

    const geofence = geofenceList.value.find(item => item.id === id);
    if (!geofence) {
      notification.error({
        message: '删除失败',
        description: '找不到指定的地理围栏',
      });
      return;
    }

    const response = await deleteGeofenceApi(id);

    if (response.success) {
      // 从列表中移除
      const index = geofenceList.value.findIndex(item => item.id === id);
      if (index !== -1) {
        geofenceList.value.splice(index, 1);
      }

      // 从地图移除
      mapRef.value?.removeGeofence(id);

      notification.success({
        message: '删除成功',
        description: `地理围栏 "${geofence.name}" 已删除`,
      });
    } else {
      notification.error({
        message: '删除失败',
        description: response.message || '删除地理围栏时发生错误',
      });
    }
  } catch (error) {
    console.error('Failed to delete geofence:', error);
    notification.error({
      message: '删除失败',
      description: '无法删除地理围栏，请检查网络连接或稍后重试',
    });
  } finally {
    loading.value = false;
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
  const restrictedZones = geofenceList.value.filter(
    (item) => item.type === 'RESTRICTED_ZONE',
  ).length;

  return {
    total: geofenceList.value.length,
    noFlyZones,
    flyZones,
    restrictedZones,
  };
});

// 监听地理围栏类型变化，自动清空无人机关联
watch(() => geofenceForm.type, (newType) => {
  // 如果不是限制区，清空无人机关联
  if (newType !== 'RESTRICTED_ZONE') {
    geofenceForm.droneIds = [];
  }
});

// 键盘事件处理
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && isDrawing.value) {
    cancelDrawing();
  }
};

onMounted(async () => {
  console.log('Geofence component mounted, starting initialization...');

  document.addEventListener('keydown', handleKeydown);

  try {
    // 测试API连接
    console.log('Testing API connection...');
    await testApiConnection();

    // 加载地理围栏数据
    console.log('Loading geofences...');
    await loadGeofences();

    // 加载无人机列表
    console.log('Loading drone list...');
    await loadDroneList();

    console.log('Initialization completed successfully');
  } catch (error) {
    console.error('Initialization failed:', error);
    notification.error({
      message: '初始化失败',
      description: '组件初始化过程中发生错误，请刷新页面重试',
    });
  }
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
          <!-- API连接状态指示器 -->
          <div class="flex items-center space-x-2">
            <div
              :class="[
                'w-2 h-2 rounded-full',
                apiConnected ? 'bg-green-500' : 'bg-red-500'
              ]"
            ></div>
            <span class="text-sm text-gray-500">
              {{ apiConnected ? '后端已连接' : '后端连接异常' }}
            </span>
          </div>

          <div class="text-sm text-gray-500">
            总计: {{ geofenceStats.total }} | 禁飞区:
            {{ geofenceStats.noFlyZones }} | 允飞区:
            {{ geofenceStats.flyZones }} | 受限区:
            {{ geofenceStats.restrictedZones }}
          </div>
          <Button
            type="primary"
            size="large"
            :loading="isDrawing"
            @click="startDrawing"
            :disabled="isDrawing || !apiConnected"
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
              <span class="text-red-600">🚫 禁飞区</span>
              <span class="ml-2 text-sm text-gray-500">禁止一切无人机进入</span>
            </Radio>
            <Radio value="FLY_ZONE">
              <span class="text-green-600">✅ 允飞区</span>
              <span class="ml-2 text-sm text-gray-500">不限制任何无人机在此处飞行</span>
            </Radio>
            <Radio value="RESTRICTED_ZONE">
              <span class="text-yellow-600">⚠️ 限制区</span>
              <span class="ml-2 text-sm text-gray-500">仅对有权限的飞机开放权限</span>
            </Radio>
          </Radio.Group>

          <!-- 类型说明 -->
          <div class="mt-3 p-3 bg-gray-50 rounded-lg">
            <div v-if="geofenceForm.type === 'NO_FLY_ZONE'" class="text-sm text-red-600">
              <strong>禁飞区：</strong>禁止一切无人机进入，不需要关联无人机。
            </div>
            <div v-else-if="geofenceForm.type === 'FLY_ZONE'" class="text-sm text-green-600">
              <strong>允飞区：</strong>不限制任何无人机在此处飞行，不需要关联无人机。
            </div>
            <div v-else-if="geofenceForm.type === 'RESTRICTED_ZONE'" class="text-sm text-yellow-600">
              <strong>限制区：</strong>仅对有权限的飞机开放权限，需要关联具有权限的无人机。
            </div>
          </div>
        </Form.Item>

        <Form.Item label="高度限制" class="mb-4">
          <div class="space-y-3">
            <div class="flex items-center space-x-3">
              <label class="w-20 text-sm text-gray-600">最低高度:</label>
              <Input
                v-model:value="geofenceForm.altitudeMin"
                type="number"
                placeholder="米 (可选)"
                :min="0"
                :max="500"
                class="w-32"
                addonAfter="m"
              />
              <span class="text-xs text-gray-400">留空表示不限制</span>
            </div>
            <div class="flex items-center space-x-3">
              <label class="w-20 text-sm text-gray-600">最高高度:</label>
              <Input
                v-model:value="geofenceForm.altitudeMax"
                type="number"
                placeholder="米 (可选)"
                :min="0"
                :max="500"
                class="w-32"
                addonAfter="m"
              />
              <span class="text-xs text-gray-400">留空表示不限制</span>
            </div>
            <div class="text-xs text-blue-600 bg-blue-50 p-2 rounded">
              💡 提示：高度限制仅在禁飞区和限制区生效，允飞区通常不需要设置高度限制
            </div>
          </div>
        </Form.Item>

        <!-- 只有限制区才显示无人机关联选项 -->
        <Form.Item v-if="geofenceForm.type === 'RESTRICTED_ZONE'" label="关联无人机" required>
          <Select
            v-model:value="geofenceForm.droneIds"
            mode="multiple"
            placeholder="选择有权限在此限制区飞行的无人机"
            :options="droneList.map((drone) => ({
                value: drone.id,
                label: `${drone.serialNumber} - ${drone.name}`,
            }))"
            allow-clear
          />
          <div class="mt-2 text-xs text-yellow-600 bg-yellow-50 p-2 rounded">
            ⚠️ 限制区必须关联无人机，只有关联的无人机才能在此区域飞行
          </div>
        </Form.Item>

        <!-- 禁飞区和允飞区的说明 -->
        <div v-else-if="geofenceForm.type === 'NO_FLY_ZONE'" class="mb-4">
          <div class="text-xs text-red-600 bg-red-50 p-2 rounded">
            🚫 禁飞区不需要关联无人机，将禁止所有无人机进入
          </div>
        </div>

        <div v-else-if="geofenceForm.type === 'FLY_ZONE'" class="mb-4">
          <div class="text-xs text-green-600 bg-green-50 p-2 rounded">
            ✅ 允飞区不需要关联无人机，所有无人机都可以在此区域自由飞行
          </div>
        </div>

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
