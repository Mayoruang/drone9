import { ref } from 'vue';
import type { DroneData, DroneStatus } from '../store/modules/drone';
import { statusColors } from '../store/modules/drone';

// 扩展Window接口
declare global {
  interface Window {
    BMap: any;
    BMap_Symbol_SHAPE_POINT: any;
    BMap_Symbol_SHAPE_PLANE: any;
    BMap_Symbol_SHAPE_WARNING: any;
    BMap_loadScriptTime: number;
    initMapInstance: () => void;
  }
}

export class BaiduMapService {
  private mapInstance: any = null;
  private markers: Map<string, any> = new Map();
  private circles: Map<string, any> = new Map();
  private apiKey: string;
  private scriptContainer: HTMLDivElement | null = null;
  private debug: boolean = false;

  private mapReady = ref(false);
  private mapError = ref<string | null>(null);
  private loading = ref(true);
  private center = ref({ lng: 123.4315, lat: 41.8057 }); // 默认沈阳市中心
  private zoom = ref(12);

  constructor(apiKey: string = 'PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE', debug: boolean = false) {
    this.apiKey = apiKey;
    this.debug = debug;
  }

  /**
   * 获取地图状态
   */
  public getState() {
    return {
      mapReady: this.mapReady,
      mapError: this.mapError,
      loading: this.loading,
      center: this.center,
      zoom: this.zoom
    };
  }

  /**
   * 初始化地图
   * @param containerId 地图容器ID
   * @param options 初始化选项
   */
  public async init(
    containerId: string,
    options: {
      center?: { lng: number; lat: number };
      zoom?: number;
      showControls?: boolean;
    } = {}
  ): Promise<boolean> {
    this.loading.value = true;

    try {
      // 设置参数
      if (options.center) this.center.value = options.center;
      if (options.zoom) this.zoom.value = options.zoom;

      // 加载地图API
      const apiLoaded = await this.loadBaiduMapAPI();
      if (!apiLoaded) {
        this.mapError.value = '百度地图API加载失败';
        this.loading.value = false;
        return false;
      }

      // 初始化地图实例
      const BMap = window.BMap;
      if (!BMap) {
        this.mapError.value = 'BMap API未加载';
        this.loading.value = false;
        return false;
      }

      // 获取地图容器
      const container = document.getElementById(containerId);
      if (!container) {
        this.mapError.value = `找不到地图容器: #${containerId}`;
        this.loading.value = false;
        return false;
      }

      // 创建地图实例
      this.log('创建地图实例...');
      this.mapInstance = new BMap.Map(containerId);

      // 设置中心点和缩放级别
      const point = new BMap.Point(this.center.value.lng, this.center.value.lat);
      this.mapInstance.centerAndZoom(point, this.zoom.value);

      // 添加控件
      if (options.showControls !== false) {
        this.mapInstance.addControl(new BMap.NavigationControl());
        this.mapInstance.addControl(new BMap.ScaleControl());
        this.mapInstance.enableScrollWheelZoom(true);
      }

      // 设置地图加载完成
      this.mapReady.value = true;
      this.loading.value = false;
      this.log('地图初始化完成');

      return true;
    } catch (error) {
      this.error('地图初始化失败', error);
      this.mapError.value = `地图初始化失败: ${error instanceof Error ? error.message : '未知错误'}`;
      this.loading.value = false;
      return false;
    }
  }

  /**
   * 加载百度地图API
   */
  private loadBaiduMapAPI(): Promise<boolean> {
    return new Promise((resolve) => {
      // 检查API是否已加载
      if (window.BMap) {
        this.log('百度地图API已加载');
        resolve(true);
        return;
      }

      // 创建脚本容器
      this.createScriptContainer();

      // 设置全局回调
      window.initMapInstance = () => {
        this.log('百度地图API加载完成');
        resolve(true);
      };

      // 创建脚本元素
      const script = document.createElement('script');
      script.type = 'text/javascript';
      script.src = `https://api.map.baidu.com/api?v=3.0&ak=${this.apiKey}&callback=initMapInstance`;
      script.async = true;
      script.onerror = () => {
        this.error('百度地图API加载失败');
        resolve(false);
      };

      // 添加到容器
      if (this.scriptContainer) {
        this.scriptContainer.appendChild(script);
        this.log('百度地图API脚本已添加到DOM');
      } else {
        this.error('脚本容器未创建');
        resolve(false);
      }
    });
  }

  /**
   * 创建专用的脚本容器
   */
  private createScriptContainer() {
    // 如果已存在，先移除旧容器
    if (this.scriptContainer && this.scriptContainer.parentNode) {
      this.scriptContainer.parentNode.removeChild(this.scriptContainer);
    }

    // 创建新容器
    const container = document.createElement('div');
    container.id = 'baiduMapScriptContainer-' + Date.now();
    container.style.display = 'none';
    document.body.appendChild(container);
    this.scriptContainer = container;
    return container;
  }

  /**
   * 获取地图实例
   */
  public getMapInstance() {
    return this.mapInstance;
  }

  /**
   * 更新无人机标记
   * @param drones 无人机数据
   * @param onClick 点击回调
   */
  public updateDroneMarkers(
    drones: DroneData[],
    onClick?: (drone: DroneData) => void
  ) {
    if (!this.mapInstance || !this.mapReady.value) {
      this.error('地图未初始化，无法更新标记');
      return;
    }

    if (!window.BMap) {
      this.error('BMap API未加载');
      return;
    }

    const BMap = window.BMap;

    // 记录当前更新的无人机ID
    const updatedIds = new Set<string>();

    // 为每个无人机创建或更新标记
    drones.forEach(drone => {
      const droneId = drone.droneId;
      updatedIds.add(droneId);

      // 创建点坐标
      const point = new BMap.Point(drone.position.longitude, drone.position.latitude);

      // 检查是否已有标记
      if (this.markers.has(droneId)) {
        // 更新现有标记
        const marker = this.markers.get(droneId);

        // 更新位置
        marker.setPosition(point);

        // 更新图标
        const icon = this.createDroneIcon(drone);
        marker.setIcon(icon);

        // 更新文本标签
        const label = marker.getLabel();
        if (label) {
          label.setContent(drone.serialNumber);
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
        }
      } else {
        // 创建新标记
        const icon = this.createDroneIcon(drone);
        const marker = new BMap.Marker(point, { icon });

        // 添加文本标签
        const labelOpts = {
          offset: new BMap.Size(20, -5) // 调整标签位置
        };
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

        // 创建信息窗口
        const infoWindow = this.createInfoWindow(drone);

        // 添加点击事件
        if (onClick) {
          marker.addEventListener('click', () => {
            onClick(drone);
          });
        }

        // 添加悬停事件
        marker.addEventListener('mouseover', () => {
          marker.openInfoWindow(infoWindow);
        });

        // 添加到地图
        this.mapInstance.addOverlay(marker);

        // 保存标记引用
        this.markers.set(droneId, marker);
      }
    });

    // 移除不再存在的标记
    this.markers.forEach((marker, id) => {
      if (!updatedIds.has(id)) {
        this.mapInstance.removeOverlay(marker);
        this.markers.delete(id);
      }
    });

    // 如果地图尚未设置中心，则设置地图视图以包含所有无人机
    if (drones.length > 0 && (!this.center.value.lng || !this.center.value.lat)) {
      this.centerMapToDrones(drones);
    }
  }

  /**
   * 创建无人机图标
   */
  private createDroneIcon(drone: DroneData) {
    const BMap = window.BMap;

    // 根据状态选择颜色
    const color = statusColors[drone.status] || statusColors.OFFLINE;

    // 创建SVG格式的图标
    const svgSize = 28; // SVG尺寸
    const strokeWidth = 2; // 线条粗细

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
  }

  /**
   * 创建信息窗口
   */
  private createInfoWindow(drone: DroneData) {
    const BMap = window.BMap;

    // 状态文本
    const statusText = {
      FLYING: '飞行中',
      IDLE: '地面待命',
      LOW_BATTERY: '低电量警告',
      TRAJECTORY_ERROR: '轨迹异常警告',
      OFFLINE: '离线',
      ONLINE: '在线',
      ERROR: '错误'
    }[drone.status] || '未知';

    // 电池颜色
    const getBatteryColor = (percentage: number) => {
      if (percentage <= 20) return '#ff4d4f';
      if (percentage <= 40) return '#faad14';
      return '#52c41a';
    };

    // 创建信息窗口内容
    const infoWindow = new BMap.InfoWindow(`
      <div style="width: 200px; padding: 5px; font-family: Arial, sans-serif;">
        <div style="font-weight: bold; color: ${statusColors[drone.status]}; margin-bottom: 5px; border-bottom: 1px solid #eee; padding-bottom: 3px;">
          ${drone.serialNumber} (${statusText})
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

    return infoWindow;
  }

  /**
   * 将地图中心设置到无人机位置
   */
  public centerMapToDrones(drones: DroneData[]) {
    if (!this.mapInstance || !this.mapReady.value || drones.length === 0) return;

    const BMap = window.BMap;

    if (drones.length === 1) {
      // 如果只有一架无人机，直接居中
      const drone = drones[0];
      if (drone) {
        const point = new BMap.Point(drone.position.longitude, drone.position.latitude);
        this.mapInstance.centerAndZoom(point, 14);
        this.log(`设置地图中心到单架无人机位置: (${drone.position.latitude}, ${drone.position.longitude})`);
      }
    } else {
      try {
        // 创建边界对象
        const bounds = new BMap.Bounds();

        // 将所有无人机位置添加到边界中
        drones.forEach(drone => {
          const point = new BMap.Point(drone.position.longitude, drone.position.latitude);
          bounds.extend(point);
        });

        // 设置地图视图以包含所有点
        this.mapInstance.setViewport(bounds);
        this.log(`设置地图视图以包含所有${drones.length}架无人机`);
      } catch (e) {
        this.error('设置地图视图出错:', e);
        // 如果出错，回退到使用第一架无人机作为中心
        const drone = drones[0];
        if (drone) {
          const point = new BMap.Point(drone.position.longitude, drone.position.latitude);
          this.mapInstance.centerAndZoom(point, 12);
        }
      }
    }
  }

  /**
   * 添加地理围栏
   */
  public addGeofence(
    droneId: string,
    center: { latitude: number; longitude: number },
    radius: number,
    options: { strokeColor?: string; fillColor?: string } = {}
  ) {
    if (!this.mapInstance || !this.mapReady.value) return;

    const BMap = window.BMap;
    const point = new BMap.Point(center.longitude, center.latitude);

    // 创建地理围栏圆形
    const circle = new BMap.Circle(point, radius, {
      strokeColor: options.strokeColor || "#1890ff",
      strokeWeight: 2,
      strokeOpacity: 0.8,
      fillColor: options.fillColor || "#1890ff",
      fillOpacity: 0.1
    });

    // 添加到地图
    this.mapInstance.addOverlay(circle);

    // 保存引用
    this.circles.set(droneId, circle);

    return circle;
  }

  /**
   * 移除地理围栏
   */
  public removeGeofence(droneId: string) {
    if (!this.mapInstance || !this.mapReady.value) return;

    const circle = this.circles.get(droneId);
    if (circle) {
      this.mapInstance.removeOverlay(circle);
      this.circles.delete(droneId);
    }
  }

  /**
   * 销毁地图
   */
  public destroy() {
    // 清除所有覆盖物
    if (this.mapInstance) {
      this.mapInstance.clearOverlays();
    }

    // 清空集合
    this.markers.clear();
    this.circles.clear();

    // 删除脚本容器
    if (this.scriptContainer && this.scriptContainer.parentNode) {
      try {
        this.scriptContainer.parentNode.removeChild(this.scriptContainer);
      } catch (e) {
        this.error('移除脚本容器时出错:', e);
      }
      this.scriptContainer = null;
    }

    // 重置状态
    this.mapInstance = null;
    this.mapReady.value = false;
    this.loading.value = false;

    this.log('地图资源已销毁');
  }

  /**
   * 日志
   */
  private log(...args: any[]) {
    if (this.debug) {
      console.log('[BaiduMapService]', ...args);
    }
  }

  /**
   * 错误日志
   */
  private error(...args: any[]) {
    console.error('[BaiduMapService]', ...args);
  }
}

// 创建默认实例
let mapService: BaiduMapService | null = null;

export function getMapService(apiKey?: string, forceNew: boolean = false): BaiduMapService {
  if (!mapService || forceNew) {
    mapService = new BaiduMapService(
      apiKey || 'PmtVSHO54O3gJgO3Z9J1VnYP07uHE3TE',
      import.meta.env.DEV
    );
  }
  return mapService;
}
