import SockJS from 'sockjs-client';
import Stomp from 'webstomp-client';
import { notification } from 'ant-design-vue';
import { useDroneStore } from '../store/modules/drone';

export class DroneWebSocketService {
  private stompClient: any = null;
  private connected: boolean = false;
  private reconnectTimer: number | null = null;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 5;
  private reconnectDelay: number = 3000; // 3秒重连延迟
  private connectTimeout: number | null = null;
  private positionSubscription: any = null;
  private deletedSubscription: any = null;
  private wsUrl: string = '';
  private debug: boolean = false;

  constructor(private baseUrl: string = 'http://localhost:8080', debug: boolean = false) {
    this.wsUrl = `${baseUrl}/ws/drones`;
    this.debug = debug;
  }

  /**
   * 初始化WebSocket连接
   */
  public connect(): Promise<boolean> {
    return new Promise((resolve) => {
      const droneStore = useDroneStore();

      // 断开已有连接
      this.disconnect();

      // 打印连接信息
      this.log(`尝试连接WebSocket: ${this.wsUrl}`);

      // 创建SockJS连接
      const sock = new SockJS(this.wsUrl);
      sock.onopen = () => this.log('SockJS连接已打开');
      sock.onerror = (e) => this.error('SockJS错误:', e);
      sock.onclose = (e) => this.log('SockJS连接已关闭:', e.reason);

      // 创建Stomp客户端
      this.stompClient = Stomp.over(sock);

      // 配置调试
      if (this.debug) {
        this.stompClient.debug = (str: string) => {
          console.log(`STOMP: ${str}`);
        };
      } else {
        this.stompClient.debug = () => {};
      }

      // 设置连接超时
      this.connectTimeout = window.setTimeout(() => {
        if (!this.connected) {
          this.error('WebSocket连接超时');
          notification.error({
            message: 'WebSocket连接超时',
            description: '无法连接到后端WebSocket服务，请检查网络连接'
          });
          resolve(false);
          this.scheduleReconnect();
        }
      }, 15000) as unknown as number;

      // 连接WebSocket服务器
      this.stompClient.connect(
        {}, // 空headers对象
        () => {
          // 清除超时
          if (this.connectTimeout !== null) {
            clearTimeout(this.connectTimeout);
            this.connectTimeout = null;
          }

          this.connected = true;
          droneStore.setWebsocketConnected(true);
          this.log('STOMP连接成功');
          this.reconnectAttempts = 0;

          // 订阅主题
          this.subscribe();

          notification.success({
            message: 'WebSocket连接成功',
            description: '已开始接收无人机实时数据'
          });

          resolve(true);
        },
        (error: any) => {
          // 清除超时
          if (this.connectTimeout !== null) {
            clearTimeout(this.connectTimeout);
            this.connectTimeout = null;
          }

          this.error('WebSocket连接失败:', error);
          this.connected = false;
          droneStore.setWebsocketConnected(false);

          notification.error({
            message: 'WebSocket连接失败',
            description: '无法接收实时数据，将在几秒后重试'
          });

          resolve(false);
          this.scheduleReconnect();
        }
      );
    });
  }

  /**
   * 订阅WebSocket主题
   */
  private subscribe() {
    if (!this.stompClient || !this.connected) {
      this.error('无法订阅，WebSocket未连接');
      return;
    }

    const droneStore = useDroneStore();

    try {
      // 订阅无人机位置更新主题
      this.log('订阅无人机位置主题');
      this.positionSubscription = this.stompClient.subscribe('/topic/drones/positions', (message: any) => {
        if (message.body) {
          try {
            const data = JSON.parse(message.body);
            this.log('收到无人机位置数据', data);

            // 处理位置数据
            if (Array.isArray(data)) {
              // 如果是数组，批量处理
              data.forEach(item => {
                droneStore.updateDrone({
                  droneId: item.droneId,
                  serialNumber: item.serialNumber,
                  model: item.model,
                  status: item.status,
                  batteryPercentage: item.batteryLevel,
                  position: {
                    latitude: item.latitude,
                    longitude: item.longitude,
                    altitude: item.altitude
                  },
                  speed: item.speed,
                  lastHeartbeat: item.lastUpdated || item.lastHeartbeat || new Date().toISOString(),
                  flightMode: item.flightMode
                });
              });
            } else if (typeof data === 'object' && data !== null) {
              // 单个对象处理
              droneStore.updateDrone({
                droneId: data.droneId,
                serialNumber: data.serialNumber,
                model: data.model,
                status: data.status,
                batteryPercentage: data.batteryLevel,
                position: {
                  latitude: data.latitude,
                  longitude: data.longitude,
                  altitude: data.altitude
                },
                speed: data.speed,
                lastHeartbeat: data.lastUpdated || data.lastHeartbeat || new Date().toISOString(),
                flightMode: data.flightMode
              });
            } else {
              this.error('无法识别的数据格式:', data);
            }
          } catch (e) {
            this.error('解析WebSocket消息时出错', e, message.body);
          }
        }
      });

      // 订阅无人机删除通知主题
      this.log('订阅无人机删除通知主题');
      this.deletedSubscription = this.stompClient.subscribe('/topic/drones/deleted', (message: any) => {
        if (message.body) {
          try {
            const data = JSON.parse(message.body);
            this.log('收到无人机删除通知', data);

            if (data.droneId) {
              // 处理无人机删除
              droneStore.removeDrone(data.droneId);
            }
          } catch (e) {
            this.error('解析WebSocket删除通知时出错', e, message.body);
          }
        }
      });

      // 连接成功后请求初始数据
      setTimeout(() => {
        this.requestDronesData();
      }, 1000);

      // 设置定时请求，每5秒请求一次最新位置
      setInterval(() => {
        if (this.connected) {
          this.requestDronesData();
        }
      }, 5000);
    } catch (e) {
      this.error('设置WebSocket订阅时出错', e);
    }
  }

  /**
   * 请求无人机位置数据
   */
  public requestDronesData() {
    if (!this.stompClient || !this.connected) return;

    try {
      this.stompClient.send('/app/requestDronesData', {}, JSON.stringify({}));
    } catch (e) {
      this.error('发送位置请求出错', e);
      // 如果发送失败，可能是连接问题，尝试重连
      this.reconnect();
    }
  }

  /**
   * 断开WebSocket连接
   */
  public disconnect() {
    if (this.stompClient && this.connected) {
      // 取消订阅
      if (this.positionSubscription) {
        try {
          this.positionSubscription.unsubscribe();
        } catch (e) {
          this.error('取消位置订阅时出错', e);
        }
        this.positionSubscription = null;
      }

      if (this.deletedSubscription) {
        try {
          this.deletedSubscription.unsubscribe();
        } catch (e) {
          this.error('取消删除通知订阅时出错', e);
        }
        this.deletedSubscription = null;
      }

      // 断开连接
      try {
        this.stompClient.disconnect();
      } catch (e) {
        this.error('断开WebSocket连接时出错', e);
      }

      this.stompClient = null;
      this.connected = false;
      useDroneStore().setWebsocketConnected(false);
      this.log('WebSocket连接已关闭');
    }

    // 清除重连定时器
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * 重新连接
   */
  public reconnect() {
    this.disconnect();
    this.scheduleReconnect();
  }

  /**
   * 调度重连
   */
  private scheduleReconnect() {
    // 清除之前的定时器
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
    }

    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts - 1);
      this.log(`计划在 ${delay}ms 后重新连接, 尝试次数: ${this.reconnectAttempts}`);

      this.reconnectTimer = window.setTimeout(() => {
        this.log(`正在尝试重新连接 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
        this.connect();
      }, delay) as unknown as number;
    } else {
      this.error(`已达到最大重连尝试次数 (${this.maxReconnectAttempts}), 停止重连`);
      notification.error({
        message: 'WebSocket连接失败',
        description: '达到最大重试次数，请刷新页面重试'
      });
    }
  }

  /**
   * 日志
   */
  private log(...args: any[]) {
    if (this.debug) {
      console.log('[DroneWebSocketService]', ...args);
    }
  }

  /**
   * 错误日志
   */
  private error(...args: any[]) {
    console.error('[DroneWebSocketService]', ...args);
  }
}

// 创建默认实例
let wsService: DroneWebSocketService | null = null;

export function getWebSocketService(baseUrl?: string, forceNew: boolean = false): DroneWebSocketService {
  if (!wsService || forceNew) {
    wsService = new DroneWebSocketService(
      baseUrl || import.meta.env.VITE_API_URL || 'http://localhost:8080',
      import.meta.env.DEV
    );
  }
  return wsService;
}
