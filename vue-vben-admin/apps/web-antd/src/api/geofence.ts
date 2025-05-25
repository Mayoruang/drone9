import { requestClient, baseRequestClient } from '#/api/request';
import { GEOFENCE_API } from '#/config/api';

// ============================================================================
// 数据类型定义
// ============================================================================

type UUID = string;
type Timestamp = string;

interface GeoPoint {
  lat: number;
  lng: number;
}

type GeoJSONCoordinate = [number, number];

interface GeoJSONPolygon {
  type: 'Polygon';
  coordinates: Array<Array<GeoJSONCoordinate>>;
}

interface ApiResponse<T = any> {
  code: number;
  message?: string;
  data?: T;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

interface OperationResponse {
  success: boolean;
  message: string;
  resourceId?: UUID;
}

interface GeofenceListItem {
  geofenceId: UUID;
  name: string;
  description?: string;
  center: [number, number];
  geometry: GeoJSONPolygon;
  geofenceType: string;
  thumbnailUrl?: string;
  active: boolean;
  altitudeMin?: number;
  altitudeMax?: number;
  priority: number;
  areaSquareMeters?: number;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

interface GeofenceDetail {
  geofenceId: UUID;
  name: string;
  description?: string;
  active: boolean;
  createdBy: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
  geometry: GeoJSONPolygon;
  altitudeMin?: number;
  altitudeMax?: number;
  startTime?: Timestamp;
  endTime?: Timestamp;
  thumbnailUrl?: string;
  drones: Array<{
    droneId: UUID;
    serialNumber: string;
    model: string;
  }>;
  geofenceType?: string;
}

interface GeofenceData {
  id: UUID;
  name: string;
  type: 'NO_FLY_ZONE' | 'FLY_ZONE' | 'RESTRICTED_ZONE';
  coordinates: GeoPoint[];
  description?: string;
  createTime: Timestamp;
  thumbnail?: string;
  droneIds?: UUID[];
  active?: boolean;
}

interface GeofenceCreateRequest {
  name: string;
  description?: string;
  geometry: GeoJSONPolygon;
  geofenceType: string;
  active?: boolean;
  altitudeMin?: number;
  altitudeMax?: number;
  startTime?: Timestamp;
  endTime?: Timestamp;
  droneIds?: UUID[];
}

interface GeofenceResponse extends OperationResponse {
  geofenceId?: UUID;
}

type SortDirection = 'ASC' | 'DESC';

interface SortParam {
  field: string;
  direction: SortDirection;
}

interface PageParam {
  page: number;
  size: number;
  sort?: SortParam[];
}

interface FilterParam {
  field: string;
  operator: 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'LIKE' | 'IN' | 'NOT_IN';
  value: any;
}

interface QueryParam extends PageParam {
  filters?: FilterParam[];
  search?: string;
}

// ============================================================================
// 数据转换工具函数
// ============================================================================

/**
 * 将前端坐标格式转换为GeoJSON格式
 * @param coordinates 前端坐标数组
 * @returns GeoJSON多边形
 */
function coordinatesToGeoJSON(coordinates: GeoPoint[]): GeoJSONPolygon {
  // 确保多边形闭合
  const coords = [...coordinates];
  if (coords.length > 0) {
    const firstCoord = coords[0];
    const lastCoord = coords[coords.length - 1];
    if (firstCoord && lastCoord &&
        (firstCoord.lat !== lastCoord.lat || firstCoord.lng !== lastCoord.lng)) {
      coords.push(firstCoord);
    }
  }

  return {
    type: 'Polygon',
    coordinates: [coords.map(coord => [coord.lng, coord.lat])]
  };
}

/**
 * 将GeoJSON坐标转换为前端格式
 * @param geoJson GeoJSON多边形
 * @returns 前端坐标数组
 */
function geoJSONToCoordinates(geoJson: GeoJSONPolygon): GeoPoint[] {
  if (!geoJson.coordinates || !geoJson.coordinates[0]) {
    return [];
  }

  return geoJson.coordinates[0].map(([lng, lat]: [number, number]) => ({ lat, lng }));
}

/**
 * 将后端列表项转换为前端格式
 * @param item 后端列表项
 * @returns 前端地理围栏数据
 */
function transformListItemToFrontend(item: GeofenceListItem): GeofenceData {
  return {
    id: item.geofenceId,
    name: item.name,
    type: 'NO_FLY_ZONE', // 默认值，需要从详情接口获取具体类型
    coordinates: [], // 需要从详情接口获取坐标
    description: item.description,
    createTime: item.createdAt,
    thumbnail: item.thumbnailUrl,
    droneIds: [],
    active: item.active
  };
}

/**
 * 将后端详情转换为前端格式
 * @param detail 后端详情数据
 * @returns 前端地理围栏数据
 */
function transformDetailToFrontend(detail: GeofenceDetail): GeofenceData {
  return {
    id: detail.geofenceId,
    name: detail.name,
    type: (detail.geofenceType || 'NO_FLY_ZONE') as 'NO_FLY_ZONE' | 'FLY_ZONE' | 'RESTRICTED_ZONE',
    coordinates: geoJSONToCoordinates(detail.geometry),
    description: detail.description,
    createTime: detail.createdAt,
    thumbnail: detail.thumbnailUrl,
    droneIds: detail.drones.map((drone: { droneId: UUID; serialNumber: string; model: string }) => drone.droneId),
    active: detail.active
  };
}

// ============================================================================
// API接口函数
// ============================================================================

/**
 * 获取地理围栏分页列表
 * @param params 查询参数
 * @returns 分页列表响应
 */
export async function getGeofenceList(params: Partial<QueryParam> = {}): Promise<PageResponse<GeofenceListItem>> {
  const { page = 0, size = 20, sort, filters, search } = params;

  const queryParams: Record<string, any> = {
    page,
    size,
  };

  // 添加排序参数
  if (sort && sort.length > 0) {
    queryParams.sort = sort.map((s: SortParam) => `${s.field},${s.direction.toLowerCase()}`);
  }

  // 添加过滤参数
  if (filters && filters.length > 0) {
    filters.forEach((filter: FilterParam, index: number) => {
      queryParams[`filter[${index}].field`] = filter.field;
      queryParams[`filter[${index}].operator`] = filter.operator;
      queryParams[`filter[${index}].value`] = filter.value;
    });
  }

  // 添加搜索参数
  if (search) {
    queryParams.search = search;
  }

  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.get(
    GEOFENCE_API.LIST,
    { params: queryParams }
  );

  return response.data;
}

/**
 * 获取所有地理围栏（用于地图显示）
 * @returns 地理围栏数据数组
 */
export async function getAllGeofences(): Promise<GeofenceData[]> {
  try {
    // 获取第一页数据以了解总数
    const firstPage = await getGeofenceList({ page: 0, size: 20 });

    // 如果总数超过20，获取所有数据
    let allItems: GeofenceListItem[];
    if (firstPage.totalElements > 20) {
      const allData = await getGeofenceList({ page: 0, size: firstPage.totalElements });
      allItems = allData.content;
    } else {
      allItems = firstPage.content;
    }

    // 并行获取所有地理围栏的详细信息
    const detailPromises = allItems.map(item => getGeofenceDetail(item.geofenceId));
    const details = await Promise.all(detailPromises);

    return details.map(transformDetailToFrontend);
  } catch (error) {
    console.error('获取地理围栏列表失败:', error);
    return [];
  }
}

/**
 * 获取地理围栏详细信息
 * @param geofenceId 地理围栏ID
 * @returns 地理围栏详情
 */
export async function getGeofenceDetail(geofenceId: UUID): Promise<GeofenceDetail> {
  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.get(
    GEOFENCE_API.DETAIL(geofenceId)
  );

  return response.data;
}

/**
 * 创建地理围栏
 * @param data 创建数据
 * @returns 创建响应
 */
export async function createGeofence(data: {
  name: string;
  type: 'FLY_ZONE' | 'NO_FLY_ZONE' | 'RESTRICTED_ZONE';
  coordinates: GeoPoint[];
  description?: string;
  droneIds?: UUID[];
  altitudeMin?: number;
  altitudeMax?: number;
  startTime?: string;
  endTime?: string;
}): Promise<GeofenceResponse> {
  const request: GeofenceCreateRequest = {
    name: data.name,
    description: data.description,
    geometry: coordinatesToGeoJSON(data.coordinates),
    geofenceType: data.type,
    active: true,
    altitudeMin: data.altitudeMin,
    altitudeMax: data.altitudeMax,
    startTime: data.startTime,
    endTime: data.endTime,
    droneIds: data.droneIds || []
  };

  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.post(
    GEOFENCE_API.CREATE,
    request
  );

  return response.data;
}

/**
 * 更新地理围栏
 * @param geofenceId 地理围栏ID
 * @param data 更新数据
 * @returns 更新响应
 */
export async function updateGeofence(
  geofenceId: UUID,
  data: GeofenceCreateRequest
): Promise<GeofenceResponse> {
  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.put(
    GEOFENCE_API.UPDATE(geofenceId),
    data
  );

  return response.data;
}

/**
 * 删除地理围栏
 * @param geofenceId 地理围栏ID
 * @returns 删除响应
 */
export async function deleteGeofence(geofenceId: UUID): Promise<GeofenceResponse> {
  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.delete(
    GEOFENCE_API.DELETE(geofenceId)
  );

  return response.data;
}

/**
 * 绑定无人机到地理围栏
 * @param geofenceId 地理围栏ID
 * @param droneIds 无人机ID列表
 * @returns 绑定响应
 */
export async function bindDronesToGeofence(
  geofenceId: UUID,
  droneIds: UUID[]
): Promise<GeofenceResponse> {
  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.post(
    GEOFENCE_API.BIND_DRONES(geofenceId),
    { droneIds }
  );

  return response.data;
}

/**
 * 从地理围栏解绑无人机
 * @param geofenceId 地理围栏ID
 * @param droneId 无人机ID
 * @returns 解绑响应
 */
export async function unbindDroneFromGeofence(
  geofenceId: UUID,
  droneId: UUID
): Promise<GeofenceResponse> {
  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.delete(
    GEOFENCE_API.UNBIND_DRONE(geofenceId, droneId)
  );

  return response.data;
}

/**
 * 测试地理围栏API连接
 * @returns 连接状态
 */
export async function testGeofenceAPI(): Promise<boolean> {
  try {
    const response = await baseRequestClient.get(GEOFENCE_API.TEST);
    return response.data && typeof response.data === 'string' && response.data.includes('working');
  } catch (error) {
    console.error('地理围栏API测试失败:', error);
    return false;
  }
}

/**
 * 批量删除地理围栏
 * @param geofenceIds 地理围栏ID列表
 * @returns 批量删除响应
 */
export async function batchDeleteGeofences(geofenceIds: UUID[]): Promise<{
  success: number;
  failed: number;
  errors: Array<{ id: UUID; error: string }>;
}> {
  const results = await Promise.allSettled(
    geofenceIds.map(id => deleteGeofence(id))
  );

  const success = results.filter(r => r.status === 'fulfilled').length;
  const failed = results.filter(r => r.status === 'rejected').length;
  const errors = results
    .map((result, index) => ({
      id: geofenceIds[index]!,
      result
    }))
    .filter(({ result }) => result.status === 'rejected')
    .map(({ id, result }) => ({
      id,
      error: (result as PromiseRejectedResult).reason?.message || '未知错误'
    }));

  return { success, failed, errors };
}

/**
 * 获取地理围栏统计信息
 * @returns 统计信息
 */
export async function getGeofenceStats(): Promise<{
  total: number;
  active: number;
  inactive: number;
  droneBindings: number;
}> {
  // 使用baseRequestClient获取原始响应，然后提取data
  const response = await baseRequestClient.get(GEOFENCE_API.STATS);

  return response.data;
}

// ============================================================================
// 导出类型
// ============================================================================

export type {
  GeofenceData,
  GeofenceListItem,
  GeofenceDetail,
  GeofenceCreateRequest,
  GeofenceResponse,
  GeoPoint,
  GeoJSONPolygon,
};
