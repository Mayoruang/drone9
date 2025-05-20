import SockJS from 'sockjs-client';
import Stomp, { Frame } from 'webstomp-client';

export interface DronePosition {
  droneId: string;
  serialNumber: string;
  model: string;
  status: string;
  latitude: number;
  longitude: number;
  altitude: number;
  batteryLevel: number;
  speed: number;
  heading: number;
  lastUpdated: string;
}

export class DroneWebSocketService {
  private stompClient: any = null;
  private connected = false;
  private subscribers: Record<string, ((data: any) => void)[]> = {
    '/topic/drones/positions': [],
  };

  /**
   * Connect to the WebSocket server
   */
  connect() {
    // Already connected
    if (this.connected) return Promise.resolve();

    return new Promise((resolve, reject) => {
      // Create SockJS instance to handle the WebSocket connection
      const socket = new SockJS('/ws/drones');

      // Create STOMP client over the SockJS connection
      this.stompClient = Stomp.over(socket);

      // Disable debug logs in production
      this.stompClient.debug = process.env.NODE_ENV === 'development' ? console.log : () => {};

      // Connect to the server
      this.stompClient.connect(
        {}, // Headers
        () => {
          this.connected = true;

          // Subscribe to the drones positions topic
          this.stompClient.subscribe('/topic/drones/positions', (message: Frame) => {
            const dronePositions = JSON.parse(message.body) as DronePosition[];
            // Notify subscribers
            this.notifySubscribers('/topic/drones/positions', dronePositions);
          });

          resolve(true);
        },
        (error: Error) => {
          console.error('Error connecting to WebSocket:', error);
          this.connected = false;
          reject(error);
        }
      );
    });
  }

  /**
   * Disconnect from the WebSocket server
   */
  disconnect() {
    if (this.stompClient) {
      this.stompClient.disconnect();
      this.connected = false;
    }
  }

  /**
   * Subscribe to a specific drone's updates
   */
  subscribeToDrone(droneId: string, callback: (data: any) => void) {
    const topic = `/topic/drones/${droneId}`;

    // Create subscription array if it doesn't exist
    if (!this.subscribers[topic]) {
      this.subscribers[topic] = [];

      // If already connected, subscribe to the topic
      if (this.connected && this.stompClient) {
        this.stompClient.subscribe(topic, (message: Frame) => {
          const droneData = JSON.parse(message.body);
          this.notifySubscribers(topic, droneData);
        });
      }
    }

    // Add the callback to subscribers
    this.subscribers[topic].push(callback);

    return () => this.unsubscribeFromDrone(droneId, callback);
  }

  /**
   * Unsubscribe from a specific drone's updates
   */
  unsubscribeFromDrone(droneId: string, callback: (data: any) => void) {
    const topic = `/topic/drones/${droneId}`;

    if (this.subscribers[topic]) {
      // Remove the callback from subscribers
      this.subscribers[topic] = this.subscribers[topic].filter(cb => cb !== callback);
    }
  }

  /**
   * Subscribe to all drone positions updates
   */
  subscribeToDronePositions(callback: (data: DronePosition[]) => void) {
    const topic = '/topic/drones/positions';
    this.subscribers[topic] = this.subscribers[topic] || [];
    this.subscribers[topic].push(callback);

    return () => this.unsubscribeFromDronePositions(callback);
  }

  /**
   * Unsubscribe from all drone positions updates
   */
  unsubscribeFromDronePositions(callback: (data: DronePosition[]) => void) {
    const topic = '/topic/drones/positions';
    if (this.subscribers[topic]) {
      this.subscribers[topic] = this.subscribers[topic].filter(cb => cb !== callback);
    }
  }

  /**
   * Notify subscribers of a topic
   */
  private notifySubscribers(topic: string, data: any) {
    if (this.subscribers[topic]) {
      this.subscribers[topic].forEach(callback => {
        try {
          callback(data);
        } catch (e) {
          console.error('Error in WebSocket subscriber callback:', e);
        }
      });
    }
  }
}

// Create a singleton instance
export const droneSocket = new DroneWebSocketService();
