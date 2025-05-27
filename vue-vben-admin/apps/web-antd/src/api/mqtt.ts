import { requestClient } from '#/api/request';

// API endpoints
const MQTT_API = {
  CONSOLE_MESSAGE: (droneId: string) => `/v1/drones/${droneId}/console-message`,
  MQTT_MESSAGE: (droneId: string) => `/v1/drones/${droneId}/mqtt-message`,
} as const;

// Types
export interface ConsoleMessageRequest {
  message: string;
  priority?: 'LOW' | 'NORMAL' | 'HIGH';
  requireAck?: boolean;
}

export interface MqttMessageRequest {
  topic: string;
  message: string;
  messageType?: string;
  qos?: 0 | 1 | 2;
  retained?: boolean;
}

export interface MqttMessageResponse {
  success: boolean;
  message: string;
  droneId: string;
  topic?: string;
  timestamp: string;
  messageId?: string;
  errorDetails?: string;
}

/**
 * Send a console message to a specific drone
 * @param droneId - The ID of the drone
 * @param message - The console message to send
 * @returns Promise with the response
 */
export async function sendConsoleMessageToDrone(
  droneId: string,
  message: string,
  priority: 'LOW' | 'NORMAL' | 'HIGH' = 'NORMAL',
  requireAck: boolean = false
): Promise<MqttMessageResponse> {
  const request: ConsoleMessageRequest = {
    message,
    priority,
    requireAck,
  };

  const response = await requestClient.post<MqttMessageResponse>(
    MQTT_API.CONSOLE_MESSAGE(droneId),
    request
  );

  return response;
}

/**
 * Send a custom MQTT message to a specific drone
 * @param droneId - The ID of the drone
 * @param request - The MQTT message request
 * @returns Promise with the response
 */
export async function sendMqttMessageToDrone(
  droneId: string,
  request: MqttMessageRequest
): Promise<MqttMessageResponse> {
  const response = await requestClient.post<MqttMessageResponse>(
    MQTT_API.MQTT_MESSAGE(droneId),
    request
  );

  return response;
} 