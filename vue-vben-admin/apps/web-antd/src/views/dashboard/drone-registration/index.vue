<script lang="ts" setup>
import { computed, onMounted, ref, h, onUnmounted } from 'vue';
import { notification, Card, Drawer, Button, Table, Statistic, Space, Tag, Input, Popconfirm } from 'ant-design-vue';
import { CheckCircleOutlined, CloseCircleOutlined, EyeOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue';
import { Map } from 'ol';
import { fromLonLat } from 'ol/proj';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import OSM from 'ol/source/OSM';
import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import { Icon, Style } from 'ol/style';
import SockJS from 'sockjs-client';
import Stomp from 'webstomp-client';

// 定义无人机注册请求类型
interface DroneRegistrationRequest {
  requestId: string;
  serialNumber: string;
  model: string;
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'DELETED';
  requestedAt: string;
  processedAt?: string;
  adminNotes?: string;
  droneId?: string;
}

// 定义 AdminAction 类型
interface AdminAction {
  requestId: string;
  action: 'APPROVE' | 'REJECT';
  rejectionReason?: string;
}

// 状态统计
const statistics = ref({
  pendingCount: 0,
  approvedCount: 0,
  rejectedCount: 0,
});

// 抽屉状态
const drawerVisible = ref(false);
const selectedDrone = ref<DroneRegistrationRequest | null>(null);
const rejectReason = ref('');

// 表格配置
const loading = ref(false);
const registrationRequests = ref<DroneRegistrationRequest[]>([]);

// 搜索和筛选
const searchText = ref('');
const filterStatus = ref<string | null>(null);

// 根据搜索和筛选条件过滤列表
const filteredList = computed(() => {
  let result = [...registrationRequests.value];

  // 应用搜索
  if (searchText.value) {
    const search = searchText.value.toLowerCase();
    result = result.filter(
      item => item.serialNumber.toLowerCase().includes(search) ||
              item.model.toLowerCase().includes(search) ||
              item.requestId.toLowerCase().includes(search)
    );
  }

  // 应用状态筛选
  if (filterStatus.value) {
    result = result.filter(item => item.status === filterStatus.value);
  }

  return result;
});

// 获取列表数据
async function fetchRegistrationList() {
  loading.value = true;
  try {
    // 添加时间戳参数，确保不使用缓存
    const timestamp = new Date().getTime();
    // 设置较大的分页大小以获取所有记录
    const url = `/api/v1/drones/registration/list?size=100&_t=${timestamp}`;

    // 使用实际API端点
    const response = await fetch(url, {
      headers: {
        'Cache-Control': 'no-cache, no-store',
        'Pragma': 'no-cache'
      }
    });

    if (!response.ok) {
      throw new Error('获取注册申请列表失败');
    }
    const data = await response.json();
    // 更新记录列表
    registrationRequests.value = data.content || [];

    // 更新统计信息
    updateStatistics();

    // 监控数据获取情况
    console.log(`获取到 ${registrationRequests.value.length} 条记录，总计 ${data.totalElements} 条`);
    console.log(`批准: ${statistics.value.approvedCount}, 拒绝: ${statistics.value.rejectedCount}, 待审批: ${statistics.value.pendingCount}`);
  } catch (error) {
    console.error('获取注册申请列表出错:', error);
    notification.error({
      message: '获取数据失败',
      description: (error as Error).message,
    });
  } finally {
    loading.value = false;
  }
}

// 更新统计数据
function updateStatistics() {
  statistics.value = {
    pendingCount: registrationRequests.value.filter(item => item.status === 'PENDING_APPROVAL').length,
    approvedCount: registrationRequests.value.filter(item => item.status === 'APPROVED').length,
    rejectedCount: registrationRequests.value.filter(item => item.status === 'REJECTED').length,
  };
}

// 查看详情
function viewDetails(record: DroneRegistrationRequest) {
  selectedDrone.value = record;
  drawerVisible.value = true;
}

// 批准申请
async function approveRegistration(record: DroneRegistrationRequest) {
  try {
    const action: AdminAction = {
      requestId: record.requestId,
      action: 'APPROVE',
    };

    // 获取保存在localStorage中的token
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');

    const response = await fetch('/api/v1/admin/registrations/action', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 如果有token则添加到请求头
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(action),
    });

    console.log('批准请求状态:', response.status);

    if (!response.ok) {
      let errorMessage = `批准申请失败: ${response.status} ${response.statusText}`;
      try {
        const errorData = await response.json();
        errorMessage = errorData.message || errorMessage;
      } catch (e) {
        // 如果无法解析JSON，使用默认错误消息
      }
      throw new Error(errorMessage);
    }

    // 解析响应数据
    const data = await response.json();

    notification.success({
      message: '批准成功',
      description: `已批准序列号为 ${record.serialNumber} 的无人机注册申请`,
    });

    // 刷新列表
    await fetchRegistrationList();
  } catch (error) {
    console.error('批准申请出错:', error);
    notification.error({
      message: '操作失败',
      description: (error as Error).message,
    });
    // 操作失败也刷新列表以确保显示正确状态
    await fetchRegistrationList();
  }
}

// 拒绝申请
async function rejectRegistration(record: DroneRegistrationRequest) {
  try {
    const action: AdminAction = {
      requestId: record.requestId,
      action: 'REJECT',
      rejectionReason: rejectReason.value,
    };

    // 获取保存在localStorage中的token
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');

    const response = await fetch('/api/v1/admin/registrations/action', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 如果有token则添加到请求头
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(action),
    });

    console.log('拒绝请求状态:', response.status);

    if (!response.ok) {
      let errorMessage = `拒绝申请失败: ${response.status} ${response.statusText}`;
      try {
        const errorData = await response.json();
        errorMessage = errorData.message || errorMessage;
      } catch (e) {
        // 如果无法解析JSON，使用默认错误消息
      }
      throw new Error(errorMessage);
    }

    // 解析响应数据
    const data = await response.json();

    notification.success({
      message: '拒绝成功',
      description: `已拒绝序列号为 ${record.serialNumber} 的无人机注册申请`,
    });

    // 清空拒绝原因
    rejectReason.value = '';

    // 刷新列表
    await fetchRegistrationList();
  } catch (error) {
    console.error('拒绝申请出错:', error);
    notification.error({
      message: '操作失败',
      description: (error as Error).message,
    });
    // 操作失败也刷新列表以确保显示正确状态
    await fetchRegistrationList();
  }
}

// 自定义状态渲染
function getStatusTag(status: string) {
  switch (status) {
    case 'PENDING_APPROVAL':
      return { color: 'processing', text: '待审批' };
    case 'APPROVED':
      return { color: 'success', text: '已批准' };
    case 'REJECTED':
      return { color: 'error', text: '已拒绝' };
    default:
      return { color: '', text: status };
  }
}

// 表格列定义
const columns = [
  {
    title: '申请ID',
    dataIndex: 'requestId',
    key: 'requestId',
    width: 220,
    ellipsis: true,
  },
  {
    title: '序列号',
    dataIndex: 'serialNumber',
    key: 'serialNumber',
    width: 160,
  },
  {
    title: '型号',
    dataIndex: 'model',
    key: 'model',
    width: 120,
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
    align: 'center' as const,
    customRender: ({ text }: { text: string }) => {
      const { color, text: statusText } = getStatusTag(text);
      return h(Tag, { color }, () => statusText);
    },
  },
  {
    title: '申请时间',
    dataIndex: 'requestedAt',
    key: 'requestedAt',
    width: 180,
  },
  {
    title: '操作',
    key: 'action',
    fixed: 'right' as const,
    width: 240,
    align: 'center' as const,
    customRender: ({ record }: { record: DroneRegistrationRequest }) => {
      if (record.status === 'PENDING_APPROVAL') {
        return h(Space, {}, [
          h(Button, {
            type: 'primary',
            size: 'small',
            onClick: () => viewDetails(record)
          }, [h(EyeOutlined), ' 详情']),
          h(Button, {
            type: 'primary',
            size: 'small',
            onClick: () => approveRegistration(record),
            style: { backgroundColor: '#52c41a', borderColor: '#52c41a' }
          }, [h(CheckCircleOutlined), ' 同意']),
          h(Popconfirm, {
            title: '拒绝申请',
            description: '确定要拒绝此申请吗？',
            onConfirm: () => rejectRegistration(record),
            okText: '确定',
            cancelText: '取消'
          }, [
            h(Button, {
              danger: true,
              size: 'small'
            }, [h(CloseCircleOutlined), ' 拒绝'])
          ])
        ]);
      } else {
        return h(Button, {
          type: 'primary',
          size: 'small',
          onClick: () => viewDetails(record)
        }, [h(EyeOutlined), '详情']);
      }
    },
  },
];

// 地图相关变量
const map = ref<Map | null>(null);
const droneLayer = ref<VectorLayer<VectorSource> | null>(null);
const droneFeatures = ref<Record<string, Feature>>({});

// WebSocket相关变量
const stompClient = ref<any>(null);
const connected = ref(false);

// 初始化地图
const initMap = () => {
  // 创建矢量图层源和图层
  const vectorSource = new VectorSource();
  const vectorLayer = new VectorLayer({
    source: vectorSource,
  });
  droneLayer.value = vectorLayer;

  // 创建地图实例
  map.value = new Map({
    target: 'map',
    layers: [
      new TileLayer({
        source: new OSM(),
      }),
      vectorLayer,
    ],
    view: new View({
      center: fromLonLat([116.3, 39.9]), // 默认北京中心
      zoom: 12,
    }),
  });
};

// 创建无人机标记样式
const createDroneStyle = (status: string) => {
  return new Style({
    image: new Icon({
      src: '/drone-icon.png',
      scale: 0.5,
      color: status === 'ONLINE' ? '#4CAF50' : '#9E9E9E',
    }),
  });
};

// 更新无人机位置
const updateDronePositions = (positions: any[]) => {
  if (!droneLayer.value) return;
  const source = droneLayer.value.getSource();
  if (!source) return;

  positions.forEach((pos) => {
    if (pos.latitude && pos.longitude) {
      const coordinates = fromLonLat([pos.longitude, pos.latitude]);

      let feature = droneFeatures.value[pos.droneId];
      if (feature) {
        // 更新现有标记
        const point = feature.getGeometry() as Point;
        point.setCoordinates(coordinates);
      } else {
        // 创建新标记
        feature = new Feature({
          geometry: new Point(coordinates),
          properties: {
            droneId: pos.droneId,
          },
        });
        feature.setStyle(createDroneStyle('ONLINE'));
        droneFeatures.value[pos.droneId] = feature;
        source.addFeature(feature);
      }
    }
  });
};

// 连接WebSocket
const connectWebSocket = () => {
  const socket = new SockJS('/ws/drones');
  stompClient.value = Stomp.over(socket);

  stompClient.value.connect(
    {},
    () => {
      connected.value = true;
      // 订阅无人机位置更新
      stompClient.value.subscribe('/topic/drones/positions', (message: any) => {
        const positions = JSON.parse(message.body);
        updateDronePositions(positions);
      });
    },
    (error: any) => {
      console.error('WebSocket连接错误:', error);
      notification.error({
        message: '无法连接到实时位置服务',
      });
      connected.value = false;
    }
  );
};

// 设置定时刷新数据
let refreshTimer: number | null = null;

// 生命周期钩子
onMounted(() => {
  // 立即获取数据
  fetchRegistrationList();

  // 初始化地图
  initMap();
  connectWebSocket();

  // 设置较短的轮询间隔（10秒）
  const refreshInterval = 10000;
  refreshTimer = window.setInterval(() => {
    fetchRegistrationList();
  }, refreshInterval);

  // 清理
  onUnmounted(() => {
    if (refreshTimer) {
      clearInterval(refreshTimer);
    }
    if (stompClient.value) {
      stompClient.value.disconnect();
    }
    if (map.value) {
      map.value.dispose();
    }
  });
});

// 删除无人机
async function deleteDrone(record: DroneRegistrationRequest) {
  try {
    // 使用测试API端点
    return await deleteWithAlternativeAPI(record);
  } catch (error) {
    console.error('删除无人机出错:', error);
    notification.error({
      message: '删除失败',
      description: (error as Error).message,
    });
  }
}

// 使用替代API端点删除无人机
async function deleteWithAlternativeAPI(record: DroneRegistrationRequest) {
  // 确保有droneId
  if (!record.droneId) {
    notification.error({
      message: '无法删除',
      description: '无人机ID不存在',
    });
    throw new Error('无人机ID不存在');
  }

  // 使用GET方法的测试API
  const response = await fetch(`/api/v1/drones/test/delete/${record.droneId}`, {
    method: 'GET'
  });

  if (!response.ok) {
    let errorMessage = `删除失败: ${response.status} ${response.statusText}`;
    try {
      const errorData = await response.json();
      errorMessage = errorData.message || errorMessage;
    } catch (e) {
      // 如果无法解析JSON，使用默认错误消息
    }
    throw new Error(errorMessage);
  }

  const data = await response.json();

  if (data.success) {
    notification.success({
      message: '删除成功',
      description: `已成功删除序列号为 ${record.serialNumber} 的无人机`,
    });

    // 更新本地列表中的注册记录状态
    const index = registrationRequests.value.findIndex(item => item.requestId === record.requestId);
    if (index !== -1) {
      const updatedRecord = registrationRequests.value[index];
      if (updatedRecord) {
        updatedRecord.status = 'DELETED' as const;
        updatedRecord.droneId = undefined;
      }
    }

    // 关闭抽屉
    drawerVisible.value = false;

    // 重新获取列表数据
    await fetchRegistrationList();

    return data;
  } else {
    notification.error({
      message: '删除失败',
      description: data.message || '未知错误',
    });
    throw new Error(data.message || '未知错误');
  }
}
</script>

<template>
  <div class="p-5">
    <div class="mb-5">
      <h2 class="text-lg font-bold mb-4">无人机注册管理</h2>
      <div class="flex justify-between gap-4">
        <!-- 待审批统计卡片 -->
        <Card class="flex-1 shadow-sm">
          <Statistic
            title="待审批"
            :value="statistics.pendingCount"
            :value-style="{ color: '#1890ff' }"
          />
        </Card>

        <!-- 已注册统计卡片 -->
        <Card class="flex-1 shadow-sm">
          <Statistic
            title="已注册"
            :value="statistics.approvedCount"
            :value-style="{ color: '#52c41a' }"
          />
        </Card>

        <!-- 已拒绝统计卡片 -->
        <Card class="flex-1 shadow-sm">
          <Statistic
            title="已拒绝"
            :value="statistics.rejectedCount"
            :value-style="{ color: '#ff4d4f' }"
          />
        </Card>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="mb-4 flex justify-between items-center">
      <div class="flex items-center space-x-4">
        <Input
          v-model:value="searchText"
          placeholder="搜索序列号/型号"
          style="width: 250px;"
          allow-clear
        />
        <Space>
          <Button
            :type="filterStatus === null ? 'primary' : 'default'"
            @click="filterStatus = null"
          >
            全部
          </Button>
          <Button
            :type="filterStatus === 'PENDING_APPROVAL' ? 'primary' : 'default'"
            @click="filterStatus = 'PENDING_APPROVAL'"
          >
            待审批
          </Button>
          <Button
            :type="filterStatus === 'APPROVED' ? 'primary' : 'default'"
            @click="filterStatus = 'APPROVED'"
          >
            已批准
          </Button>
          <Button
            :type="filterStatus === 'REJECTED' ? 'primary' : 'default'"
            @click="filterStatus = 'REJECTED'"
          >
            已拒绝
          </Button>
        </Space>
      </div>

      <Button
        type="primary"
        @click="fetchRegistrationList"
        :loading="loading"
      >
        <template #icon><ReloadOutlined /></template>
        刷新
      </Button>
    </div>

    <!-- 列表 -->
    <Card class="shadow-sm">
      <Table
        :columns="columns"
        :data-source="filteredList"
        :loading="loading"
        :scroll="{ x: 1000 }"
        row-key="requestId"
        bordered
        size="middle"
        :pagination="{
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (total) => `共 ${total} 条`,
          defaultPageSize: 10,
          position: ['bottomRight']
        }"
        :locale="{
          emptyText: '暂无数据'
        }"
      />
    </Card>

    <!-- 详情抽屉 -->
    <Drawer
      title="无人机注册申请详情"
      placement="right"
      :width="500"
      :open="drawerVisible"
      @close="drawerVisible = false"
      :footer-style="{ textAlign: 'right' }"
    >
      <div v-if="selectedDrone">
        <Card title="基本信息" class="mb-4">
          <p class="mb-2"><strong>申请ID:</strong> {{ selectedDrone.requestId }}</p>
          <p class="mb-2"><strong>序列号:</strong> {{ selectedDrone.serialNumber }}</p>
          <p class="mb-2"><strong>型号:</strong> {{ selectedDrone.model }}</p>
          <p class="mb-2">
            <strong>状态:</strong>
            <span v-if="selectedDrone.status === 'PENDING_APPROVAL'">
              <Tag color="processing">待审批</Tag>
            </span>
            <span v-else-if="selectedDrone.status === 'APPROVED'">
              <Tag color="success">已批准</Tag>
            </span>
            <span v-else-if="selectedDrone.status === 'REJECTED'">
              <Tag color="error">已拒绝</Tag>
            </span>
          </p>
          <p class="mb-2"><strong>申请时间:</strong> {{ selectedDrone.requestedAt }}</p>
          <p v-if="selectedDrone.processedAt" class="mb-2">
            <strong>处理时间:</strong> {{ selectedDrone.processedAt }}
          </p>
          <p v-if="selectedDrone.droneId" class="mb-2">
            <strong>无人机ID:</strong> {{ selectedDrone.droneId }}
          </p>
        </Card>

        <Card v-if="selectedDrone.adminNotes" title="管理员备注" class="mb-4">
          <p>{{ selectedDrone.adminNotes }}</p>
        </Card>

        <div v-if="selectedDrone.status === 'PENDING_APPROVAL'" class="mt-4">
          <h3 class="font-medium mb-2">审批操作</h3>
          <Space direction="vertical" style="width: 100%">
            <Button
              type="primary"
              block
              @click="approveRegistration(selectedDrone)"
            >
              <template #icon><CheckCircleOutlined /></template>
              批准申请
            </Button>

            <div>
              <Input.TextArea
                v-model:value="rejectReason"
                placeholder="请输入拒绝原因（可选）"
                :rows="3"
                class="mb-2"
              />
              <Button
                danger
                block
                @click="rejectRegistration(selectedDrone)"
              >
                <template #icon><CloseCircleOutlined /></template>
                拒绝申请
              </Button>
            </div>
          </Space>
        </div>

        <div v-if="selectedDrone.status === 'APPROVED' && selectedDrone.droneId" class="mt-4">
          <h3 class="font-medium mb-2">管理操作</h3>
          <Popconfirm
            title="确定要删除该无人机吗?"
            description="删除后将无法恢复，无人机将从系统中永久移除"
            @confirm="deleteDrone(selectedDrone)"
            okText="确定"
            cancelText="取消"
            okType="danger"
          >
            <Button danger block>
              <template #icon><DeleteOutlined /></template>
              删除无人机
            </Button>
          </Popconfirm>
        </div>
      </div>
      <template #footer>
        <Button @click="drawerVisible = false">关闭</Button>
      </template>
    </Drawer>
  </div>
</template>

<style scoped>
.ant-card {
  border-radius: 8px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.ant-table-wrapper {
  @apply overflow-hidden;
}

/* 表格行样式 */
.ant-table-tbody > tr.ant-table-row:hover > td {
  @apply bg-blue-50;
}

/* 统计卡片样式 */
.ant-statistic-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
}

.ant-statistic-content {
  font-weight: 600;
}

/* 工具栏样式 */
.space-x-4 button {
  min-width: 70px;
}

/* 表格样式 */
.ant-table-thead > tr > th {
  font-weight: 600;
  background-color: #f5f7fa;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .flex {
    @apply flex-col;
  }

  .space-x-4 {
    @apply space-x-0 space-y-2;
  }

  .mb-4 {
    @apply mb-2;
  }
}
</style>
