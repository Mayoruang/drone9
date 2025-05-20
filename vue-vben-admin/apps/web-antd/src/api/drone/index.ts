import { requestClient as defHttp } from '../request';

enum Api {
  AllDrones = '/api/v1/monitor/drones',
  DronesByStatus = '/api/v1/monitor/drones/status',
  DroneDetail = '/api/v1/monitor/drones',
  LatestTelemetry = '/api/v1/monitor/drones/{id}/telemetry/latest',
  TelemetryHistory = '/api/v1/monitor/drones/{id}/telemetry/history',
  DronesForMap = '/api/v1/monitor/drones/map',
}

export interface DroneStatus {
  droneId: string;
  serialNumber: string;
  model: string;
  status: string;
  lastHeartbeat: string;
  latitude: number | null;
  longitude: number | null;
  altitude: number | null;
  batteryLevel: number | null;
  connected: boolean;
}

export interface DroneTelemetry {
  droneId: string;
  timestamp: string;
  batteryLevel: number | null;
  batteryVoltage: number | null;
  latitude: number | null;
  longitude: number | null;
  altitude: number | null;
  speed: number | null;
  heading: number | null;
  satellites: number | null;
  signalStrength: number | null;
  flightMode: string | null;
  temperature: number | null;
}

/**
 * Get all drones with their current status
 */
export const getAllDrones = () => {
  return defHttp.get<DroneStatus[]>(Api.AllDrones);
};

/**
 * Get drones filtered by status
 */
export const getDronesByStatus = (status: string) => {
  return defHttp.get<DroneStatus[]>(`${Api.DronesByStatus}/${status}`);
};

/**
 * Get status information for a specific drone
 */
export const getDroneStatus = (droneId: string) => {
  return defHttp.get<DroneStatus>(`${Api.DroneDetail}/${droneId}`);
};

/**
 * Get latest telemetry data for a specific drone
 */
export const getLatestTelemetry = (droneId: string) => {
  return defHttp.get<DroneTelemetry>(Api.LatestTelemetry.replace('{id}', droneId));
};

/**
 * Get historical telemetry data for a drone within a time range
 */
export const getTelemetryHistory = (
  droneId: string,
  start: string,
  end: string,
  limit: number = 100
) => {
  return defHttp.get<DroneTelemetry[]>(
    Api.TelemetryHistory.replace('{id}', droneId),
    { params: { start, end, limit } }
  );
};

/**
 * Get online drones with their latest positions for the map
 */
export const getDronesForMap = () => {
  return defHttp.get<DroneStatus[]>(Api.DronesForMap);
};
