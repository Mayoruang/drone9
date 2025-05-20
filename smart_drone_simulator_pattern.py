import argparse
import json
import random
import time
import uuid
import threading
import requests
import paho.mqtt.client as mqtt
import math

# --- Configuration & Constants ---
DEFAULT_BACKEND_URL = "http://localhost:8080/api/v1"
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_MODEL = "SimDrone-PatternFlyer"
DEFAULT_TELEMETRY_INTERVAL_SEC = 2 # Faster for pattern flight visualization
DEFAULT_POLL_INTERVAL_SEC = 10
DEFAULT_INITIAL_LATITUDE = 34.0522
DEFAULT_INITIAL_LONGITUDE = -118.2437
DEFAULT_INITIAL_ALTITUDE = 0.0 # meters (ground idle)
DEFAULT_BATTERY_LEVEL = 100.0 # percentage
DEFAULT_SPEED = 5.0 # m/s

# Flight Statuses
STATUS_IDLE = "IDLE"
STATUS_REGISTERING = "REGISTERING"
STATUS_PENDING_APPROVAL = "PENDING_APPROVAL"
STATUS_CONNECTING_MQTT = "CONNECTING_MQTT"
# STATUS_ONLINE = "ONLINE" # Replaced by IDLE for clarity when on ground after connection
STATUS_TAKING_OFF = "TAKING_OFF"
STATUS_FLYING = "FLYING" # General GOTO flight
STATUS_PATTERN_FLYING = "PATTERN_FLYING"
STATUS_LANDING = "LANDING"
STATUS_RTL = "RETURNING_TO_LAUNCH" 
STATUS_ERROR = "ERROR"

# Earth radius in meters
EARTH_RADIUS_METERS = 6371000.0

# --- Global Variables ---
drone_id_internal = None 
mqtt_client = None
mqtt_connected = threading.Event()
stop_event = threading.Event()

current_latitude = DEFAULT_INITIAL_LATITUDE
current_longitude = DEFAULT_INITIAL_LONGITUDE
current_altitude = DEFAULT_INITIAL_ALTITUDE
current_battery = DEFAULT_BATTERY_LEVEL
current_flight_status = STATUS_IDLE 
current_speed = 0.0

target_latitude = None
target_longitude = None
target_altitude = None

waypoints = []
current_waypoint_index = 0
pattern_loop_global = True 
serial_number_global = ""

ARGS = None # Will be populated by argparse

# --- Helper Functions ---
def get_serial_number(provided_serial):
    if provided_serial:
        return provided_serial
    return f"SIM-PAT-{str(uuid.uuid4())[:8].upper()}"

def log_info(message):
    print(f"[*] SIM INFO: {message}")

def log_error(message):
    print(f"[!] SIM ERROR: {message}")

def log_warn(message):
    print(f"[W] SIM WARN: {message}")

def log_debug(message):
    if ARGS and ARGS.verbose:
        print(f"[D] SIM DEBUG: {message}")

def calculate_new_coords(lat, lon, bearing_deg, distance_m):
    R = EARTH_RADIUS_METERS
    d = distance_m

    lat_rad = math.radians(lat)
    lon_rad = math.radians(lon)
    bearing_rad = math.radians(bearing_deg)

    new_lat_rad = math.asin(math.sin(lat_rad) * math.cos(d / R) +
                           math.cos(lat_rad) * math.sin(d / R) * math.cos(bearing_rad))
    new_lon_rad = lon_rad + math.atan2(math.sin(bearing_rad) * math.sin(d / R) * math.cos(lat_rad),
                                     math.cos(d / R) - math.sin(lat_rad) * math.sin(new_lat_rad))
    return math.degrees(new_lat_rad), math.degrees(new_lon_rad)

# --- API Interaction ( 그대로 유지 ) ---
def register_drone(serial_num, model, backend_url):
    log_info(f"Attempting registration for S/N: {serial_num}, Model: {model}")
    payload = {"serialNumber": serial_num, "model": model, "notes": "Simulated pattern drone"}
    try:
        response = requests.post(f"{backend_url}/drones/register", json=payload, timeout=10)
        response.raise_for_status()
        data = response.json()
        log_info(f"Registration request submitted. Request ID: {data.get('requestId')}")
        return data.get("requestId")
    except requests.exceptions.RequestException as e:
        log_error(f"Registration failed: {e}")
        return None

def check_registration_status(request_id, backend_url):
    log_info(f"Polling registration status for Request ID: {request_id}")
    try:
        response = requests.get(f"{backend_url}/drones/registration/{request_id}/status", timeout=10)
        response.raise_for_status()
        data = response.json()
        log_debug(f"Poll response: {data}")
        return data
    except requests.exceptions.RequestException as e:
        log_error(f"Failed to poll registration status: {e}")
        return None

# --- MQTT Callbacks & Functions ---
def on_connect(client, userdata, flags, rc, properties=None):
    global current_flight_status, drone_id_internal, ARGS
    if rc == 0:
        log_info(f"Successfully connected to MQTT broker. Drone ID: {drone_id_internal}")
        mqtt_connected.set()
        command_topic = userdata.get("command_topic")
        if command_topic:
            client.subscribe(command_topic)
            log_info(f"Subscribed to command topic: {command_topic}")
        else:
            log_error("Command topic not found for MQTT subscription.")
        
        current_flight_status = STATUS_IDLE # Default to IDLE on ground after connect
        
        # If a flight pattern is specified, prepare to start it
        if ARGS.flight_pattern != "NONE":
            prepare_and_initiate_pattern_flight()
    else:
        log_error(f"MQTT connection failed with code {rc}")
        current_flight_status = STATUS_ERROR
        mqtt_connected.clear()

def on_disconnect(client, userdata, rc, properties=None):
    global current_flight_status
    log_info(f"Disconnected from MQTT broker (rc: {rc}).")
    mqtt_connected.clear()
    current_flight_status = STATUS_IDLE 

def on_message(client, userdata, msg):
    global current_flight_status, target_latitude, target_longitude, target_altitude, current_speed, waypoints
    payload_str = msg.payload.decode()
    log_info(f"Received command on topic '{msg.topic}': {payload_str}")
    try:
        command_data = json.loads(payload_str)
        action = command_data.get("type") 
        parameters = command_data.get("parameters", {})
        waypoints = [] # Clear any ongoing pattern if a new command comes
        current_waypoint_index = 0

        if action == "GOTO" or action == "GOTO_WAYPOINT":
            target_latitude = parameters.get("latitude", current_latitude)
            target_longitude = parameters.get("longitude", current_longitude)
            target_altitude = parameters.get("altitude", current_altitude)
            current_speed = parameters.get("speed", ARGS.flight_speed if ARGS.flight_speed else DEFAULT_SPEED)
            current_flight_status = STATUS_FLYING
            log_info(f"Executing GOTO: Lat={target_latitude}, Lon={target_longitude}, Alt={target_altitude}, Speed={current_speed}")
        elif action == "LAND":
            current_flight_status = STATUS_LANDING
            current_speed = 1.0 
            log_info("Executing LAND")
        elif action == "RTL": 
            current_flight_status = STATUS_RTL
            target_latitude = ARGS.rectangle_lat_start if ARGS.flight_pattern != "NONE" else DEFAULT_INITIAL_LATITUDE # RTL to pattern start or default home
            target_longitude = ARGS.rectangle_lon_start if ARGS.flight_pattern != "NONE" else DEFAULT_INITIAL_LONGITUDE
            target_altitude = ARGS.flight_altitude if ARGS.flight_pattern != "NONE" and ARGS.flight_altitude > 0 else DEFAULT_INITIAL_ALTITUDE
            current_speed = DEFAULT_SPEED * 0.8 
            log_info("Executing RTL")
        elif action == "TAKEOFF":
            target_altitude = parameters.get("altitude", current_altitude + 10) 
            target_latitude = current_latitude # Take off vertically
            target_longitude = current_longitude
            current_flight_status = STATUS_TAKING_OFF 
            current_speed = 2.0 
            log_info(f"Executing TAKEOFF to altitude: {target_altitude}")
        else:
            log_warn(f"Unknown command action: {action}")
    except json.JSONDecodeError:
        log_error(f"Failed to parse command JSON: {payload_str}")
    except Exception as e:
        log_error(f"Error processing command: {e}")

def connect_mqtt(host, port, username, password, command_topic):
    global mqtt_client, serial_number_global
    client_id = f"sim-drone-{serial_number_global}-{str(uuid.uuid4())[:4]}"
    mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
    mqtt_client.on_connect = on_connect
    mqtt_client.on_disconnect = on_disconnect
    mqtt_client.on_message = on_message
    mqtt_client.user_data_set({"command_topic": command_topic})
    if username and password:
        mqtt_client.username_pw_set(username, password)
    try:
        log_info(f"Attempting to connect to MQTT: {host}:{port} as {client_id}")
        mqtt_client.connect(host, port, keepalive=60)
        mqtt_client.loop_start() 
        return True
    except Exception as e:
        log_error(f"MQTT connection setup failed: {e}")
        return False

# --- Drone Simulation Logic ---
def generate_rectangle_waypoints(start_lat, start_lon, width_m, height_m, altitude_m):
    global waypoints, current_waypoint_index
    waypoints = []
    p1 = (start_lat, start_lon, altitude_m)
    waypoints.append(p1)
    p2_lat, p2_lon = calculate_new_coords(p1[0], p1[1], 90, width_m)
    waypoints.append((p2_lat, p2_lon, altitude_m))
    p3_lat, p3_lon = calculate_new_coords(p2_lat, p2_lon, 0, height_m)
    waypoints.append((p3_lat, p3_lon, altitude_m))
    p4_lat, p4_lon = calculate_new_coords(p3_lat, p3_lon, 270, width_m)
    waypoints.append((p4_lat, p4_lon, altitude_m))
    if pattern_loop_global:
        waypoints.append(p1) 
    log_info(f"Generated {len(waypoints)} rectangle waypoints.") # Removed waypoints list from log for brevity
    log_debug(f"Waypoints: {waypoints}")

def prepare_and_initiate_pattern_flight():
    global current_flight_status, target_altitude, target_latitude, target_longitude, current_speed, ARGS
    if ARGS.flight_pattern == "RECTANGLE":
        log_info("Preparing RECTANGLE flight pattern...")
        generate_rectangle_waypoints(
            ARGS.rectangle_lat_start, ARGS.rectangle_lon_start,
            ARGS.rectangle_width, ARGS.rectangle_height, ARGS.flight_altitude
        )
    # Add other patterns like CIRCLE here in elif blocks
    else:
        log_info("No specific flight pattern requested or unknown pattern.")
        current_flight_status = STATUS_IDLE
        return

    if not waypoints:
        log_warn("Waypoint generation failed. Drone remains IDLE.")
        current_flight_status = STATUS_IDLE
        return

    # If on ground, set status to TAKING_OFF to reach pattern altitude first
    if current_altitude < ARGS.flight_altitude - 0.1: # Check if significantly below pattern altitude
        log_info(f"Drone on ground or below pattern altitude ({current_altitude}m). Taking off to {ARGS.flight_altitude}m.")
        target_altitude = ARGS.flight_altitude
        target_latitude = current_latitude # Take off vertically
        target_longitude = current_longitude
        current_speed = 2.0 # Takeoff speed
        current_flight_status = STATUS_TAKING_OFF
    else: # Already at or above pattern altitude, start pattern directly
        start_pattern_flight_at_first_waypoint()

def start_pattern_flight_at_first_waypoint():
    global current_flight_status, target_latitude, target_longitude, target_altitude, current_speed, waypoints, current_waypoint_index, ARGS
    if not waypoints: 
        log_warn("No waypoints to start pattern flight.")
        current_flight_status = STATUS_IDLE
        return
    current_waypoint_index = 0
    next_wp = waypoints[current_waypoint_index]
    target_latitude, target_longitude, target_altitude = next_wp
    current_flight_status = STATUS_PATTERN_FLYING
    current_speed = ARGS.flight_speed 
    log_info(f"Starting pattern flight. First waypoint ({current_waypoint_index+1}/{len(waypoints)}): {next_wp}. Speed: {current_speed} m/s")

def simulate_movement_and_battery():
    global current_latitude, current_longitude, current_altitude, current_battery, current_flight_status
    global current_speed, target_latitude, target_longitude, target_altitude, waypoints, current_waypoint_index, pattern_loop_global, ARGS

    drain_rate = 0.01 # Base drain rate
    if current_flight_status not in [STATUS_IDLE, STATUS_PENDING_APPROVAL, STATUS_REGISTERING, STATUS_CONNECTING_MQTT]:
        drain_rate = 0.1 
        if current_flight_status in [STATUS_FLYING, STATUS_PATTERN_FLYING, STATUS_RTL, STATUS_TAKING_OFF]:
            drain_rate = 0.2 # Higher drain for active flight
    current_battery -= drain_rate
    if current_battery < 0: current_battery = 0
    
    if current_battery == 0 and current_flight_status not in [STATUS_LANDING, STATUS_IDLE]:
        log_warn("Battery depleted! Forcing LAND.")
        current_flight_status = STATUS_LANDING
        waypoints = [] 

    if current_flight_status == STATUS_TAKING_OFF:
        if target_altitude is not None and current_altitude < target_altitude - 0.1:
            current_altitude += current_speed * ARGS.telemetry_interval * 0.2 # Simplified vertical speed factor
            current_altitude = min(current_altitude, target_altitude)
            log_debug(f"Taking off: Alt={current_altitude:.1f}m / {target_altitude}m")
        else:
            current_altitude = target_altitude
            log_info(f"Reached takeoff altitude: {current_altitude:.1f}m.")
            if ARGS.flight_pattern != "NONE" and waypoints: # Was taking off for a pattern
                start_pattern_flight_at_first_waypoint()
            else: # General takeoff command complete
                current_flight_status = STATUS_IDLE 
                current_speed = 0.0

    elif current_flight_status == STATUS_FLYING or current_flight_status == STATUS_RTL or current_flight_status == STATUS_PATTERN_FLYING:
        if target_latitude is None: # No active target (e.g. after a command clear)
            current_flight_status = STATUS_IDLE
            current_speed = 0.0
            return
            
        lat_diff = target_latitude - current_latitude
        lon_diff = target_longitude - current_longitude
        alt_diff = target_altitude - current_altitude
        dlon_rad = math.radians(lon_diff)
        dlat_rad = math.radians(lat_diff)
        a = math.sin(dlat_rad/2)**2 + math.cos(math.radians(current_latitude)) * math.cos(math.radians(target_latitude)) * math.sin(dlon_rad/2)**2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
        ground_distance = EARTH_RADIUS_METERS * c
        total_distance = (ground_distance**2 + alt_diff**2)**0.5
        
        if total_distance > 0.2: # Arrival threshold
            move_distance_this_step = current_speed * ARGS.telemetry_interval
            move_fraction = min(move_distance_this_step / total_distance, 1.0) if total_distance > 0 else 1.0
            current_latitude += lat_diff * move_fraction
            current_longitude += lon_diff * move_fraction
            current_altitude += alt_diff * move_fraction
            log_debug(f"Moving ({current_flight_status}): Lat={current_latitude:.6f}, Lon={current_longitude:.6f}, Alt={current_altitude:.1f}m. Target WP idx: {current_waypoint_index if current_flight_status == STATUS_PATTERN_FLYING else 'N/A'}")
        else: # Arrived at target/waypoint
            current_latitude, current_longitude, current_altitude = target_latitude, target_longitude, target_altitude # Snap to target
            if current_flight_status == STATUS_PATTERN_FLYING:
                log_info(f"Arrived at waypoint {current_waypoint_index + 1}/{len(waypoints)}: ({target_latitude:.6f}, {target_longitude:.6f})")
                current_waypoint_index += 1
                if current_waypoint_index < len(waypoints):
                    next_wp = waypoints[current_waypoint_index]
                    target_latitude, target_longitude, target_altitude = next_wp
                    log_info(f"Proceeding to next WP {current_waypoint_index + 1}: ({target_latitude:.6f}, {target_longitude:.6f})")
                else: # Pattern complete
                    if pattern_loop_global:
                        log_info("Pattern complete. Looping...")
                        current_waypoint_index = 0
                        next_wp = waypoints[current_waypoint_index]
                        target_latitude, target_longitude, target_altitude = next_wp
                    else:
                        log_info("Pattern complete. Switching to IDLE at last waypoint.")
                        current_flight_status = STATUS_IDLE
                        current_speed = 0.0
                        waypoints = [] 
            elif current_flight_status == STATUS_RTL:
                log_info(f"Arrived at RTL target.")
                current_flight_status = STATUS_LANDING
                current_speed = 1.0 
            else: # GOTO complete
                log_info(f"Arrived at GOTO target.")
                current_flight_status = STATUS_IDLE 
                current_speed = 0.0

    elif current_flight_status == STATUS_LANDING:
        if current_altitude > 0.1: 
            current_altitude -= 1.0 * ARGS.telemetry_interval # Landing speed 1m/s adjusted by interval
            current_altitude = max(0, current_altitude)
            current_speed = 1.0 
            log_debug(f"Landing: Alt={current_altitude:.1f}m")
        else:
            current_altitude = 0.0
            current_flight_status = STATUS_IDLE 
            current_speed = 0.0
            log_info("Landed successfully.")

def publish_telemetry_periodically(telemetry_topic):
    global mqtt_client, ARGS
    while not stop_event.is_set():
        if mqtt_connected.is_set() and mqtt_client:
            simulate_movement_and_battery()
            payload = {
                "timestamp": time.time(), 
                "latitude": round(current_latitude, 6),
                "longitude": round(current_longitude, 6),
                "altitude": round(current_altitude, 2),
                "batteryLevel": round(current_battery, 1),
                "status": current_flight_status,
                "speed": round(current_speed, 2),
                "droneId": drone_id_internal 
            }
            try:
                json_payload = json.dumps(payload)
                result = mqtt_client.publish(telemetry_topic, json_payload, qos=0) # QoS 0 for frequent telemetry
                # result.wait_for_publish(timeout=1) # Shorter timeout or none for QoS 0
                log_debug(f"Published telemetry to '{telemetry_topic}': {json_payload}")
            except Exception as e:
                log_error(f"Error publishing telemetry: {e}")
        time.sleep(ARGS.telemetry_interval)

# --- Main ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Drone Simulator Agent with Pattern Flight")
    parser.add_argument("--serial_number", type=str, help="Drone serial number (default: random SIM-PAT-xxxx)")
    parser.add_argument("--model", type=str, default=DEFAULT_MODEL, help="Drone model")
    parser.add_argument("--backend_url", type=str, default=DEFAULT_BACKEND_URL, help="Backend API URL")
    parser.add_argument("--mqtt_host", type=str, default=DEFAULT_MQTT_HOST, help="MQTT broker host")
    parser.add_argument("--mqtt_port", type=int, default=DEFAULT_MQTT_PORT, help="MQTT broker port")
    parser.add_argument("--telemetry_interval", type=float, default=DEFAULT_TELEMETRY_INTERVAL_SEC, help="Telemetry publishing interval (seconds)")
    parser.add_argument("--poll_interval", type=float, default=DEFAULT_POLL_INTERVAL_SEC, help="Registration status polling interval (seconds)")
    parser.add_argument("--lat", type=float, default=DEFAULT_INITIAL_LATITUDE, help="Initial latitude of drone")
    parser.add_argument("--lon", type=float, default=DEFAULT_INITIAL_LONGITUDE, help="Initial longitude of drone")
    parser.add_argument("--flight_pattern", type=str, default="NONE", choices=["NONE", "RECTANGLE"], help="Flight pattern (NONE or RECTANGLE)")
    parser.add_argument("--rectangle_lat_start", type=float, default=DEFAULT_INITIAL_LATITUDE, help="Rectangle: starting latitude (defaults to initial drone lat if not set)")
    parser.add_argument("--rectangle_lon_start", type=float, default=DEFAULT_INITIAL_LONGITUDE, help="Rectangle: starting longitude (defaults to initial drone lon if not set)")
    parser.add_argument("--rectangle_width", type=float, default=100.0, help="Rectangle: width in meters (East-West)")
    parser.add_argument("--rectangle_height", type=float, default=50.0, help="Rectangle: height in meters (North-South)")
    parser.add_argument("--flight_altitude", type=float, default=20.0, help="Altitude for pattern flight (meters)")
    parser.add_argument("--flight_speed", type=float, default=DEFAULT_SPEED, help="Speed for pattern flight (m/s)")
    parser.add_argument("--pattern_loop", type=lambda x: (str(x).lower() == 'true'), default=True, help="Loop the flight pattern (True/False)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose debug logging")

    ARGS = parser.parse_args()
    pattern_loop_global = ARGS.pattern_loop
    current_latitude = ARGS.lat
    current_longitude = ARGS.lon
    current_altitude = DEFAULT_INITIAL_ALTITUDE # Always start on ground
    serial_number_global = get_serial_number(ARGS.serial_number)

    log_info(f"Starting Drone Pattern Simulator: S/N={serial_number_global}, Model={ARGS.model}")
    log_info(f"Initial Pos: Lat={current_latitude:.4f}, Lon={current_longitude:.4f}, Alt={current_altitude:.1f}m")
    log_info(f"Backend: {ARGS.backend_url}, MQTT: {ARGS.mqtt_host}:{ARGS.mqtt_port}")
    log_info(f"Telemetry Interval: {ARGS.telemetry_interval}s, Poll Interval: {ARGS.poll_interval}s")
    if ARGS.flight_pattern != "NONE":
        log_info(f"Flight Pattern: {ARGS.flight_pattern}, Altitude: {ARGS.flight_altitude}m, Speed: {ARGS.flight_speed}m/s, Loop: {pattern_loop_global}")
        if ARGS.flight_pattern == "RECTANGLE":
            log_info(f"Rectangle Start: Lat={ARGS.rectangle_lat_start:.4f}, Lon={ARGS.rectangle_lon_start:.4f}, WxH: {ARGS.rectangle_width}mx{ARGS.rectangle_height}m")

    telemetry_thread = None
    request_id = None
    mqtt_credentials = None
    current_flight_status = STATUS_REGISTERING

    try:
        while not stop_event.is_set():
            if current_flight_status == STATUS_REGISTERING:
                request_id = register_drone(serial_number_global, ARGS.model, ARGS.backend_url)
                if request_id:
                    current_flight_status = STATUS_PENDING_APPROVAL
                else:
                    log_info(f"Registration failed, retrying in {ARGS.poll_interval}s...")
                    time.sleep(ARGS.poll_interval)
            
            elif current_flight_status == STATUS_PENDING_APPROVAL:
                if not request_id: 
                    log_error("No request_id, re-registering."); current_flight_status = STATUS_REGISTERING; time.sleep(1); continue
                status_data = check_registration_status(request_id, ARGS.backend_url)
                if status_data:
                    reg_status = status_data.get("status")
                    log_info(f"Registration status: {reg_status}")
                    if reg_status == "APPROVED":
                        mqtt_credentials = status_data.get("mqttCredentials")
                        drone_id_internal = status_data.get("droneId") 
                        if not drone_id_internal and mqtt_credentials: drone_id_internal = mqtt_credentials.get("droneId") # fallback
                        if mqtt_credentials and drone_id_internal:
                            log_info(f"Registration APPROVED! Drone SysID: {drone_id_internal}")
                            current_flight_status = STATUS_CONNECTING_MQTT
                        else:
                            log_error("Approved, but MQTT credentials or Drone ID missing. Waiting...")
                    elif reg_status == "REJECTED":
                        log_error(f"Registration REJECTED: {status_data.get('message', 'N/A')}. Exiting."); stop_event.set(); break 
                else:
                    log_warn("Failed to get registration status, retrying.")
                time.sleep(ARGS.poll_interval)

            elif current_flight_status == STATUS_CONNECTING_MQTT:
                if mqtt_credentials:
                    broker_connect_url = mqtt_credentials.get('mqttBrokerUrl')
                    host_for_paho, port_for_paho = ARGS.mqtt_host, ARGS.mqtt_port # Defaults
                    if broker_connect_url: # Prefer backend provided URL
                        try:
                            scheme_removed = broker_connect_url.replace("tcp://", "").replace("ws://", "")
                            parts = scheme_removed.split(":")
                            host_for_paho = parts[0]
                            if len(parts) > 1: port_for_paho = int(parts[1])
                        except Exception as e:
                            log_warn(f"Could not parse broker_url '{broker_connect_url}' from backend: {e}. Using defaults.")
                    
                    username = mqtt_credentials.get("mqttUsername")
                    password = mqtt_credentials.get("mqttPassword") 
                    command_topic = mqtt_credentials.get("mqttTopicCommands")
                    telemetry_topic = mqtt_credentials.get("mqttTopicTelemetry")

                    if not all([host_for_paho, username, command_topic, telemetry_topic]):
                        log_error("Incomplete MQTT credentials. Retrying poll.")
                        current_flight_status, mqtt_credentials = STATUS_PENDING_APPROVAL, None
                        time.sleep(ARGS.poll_interval); continue
                    
                    if connect_mqtt(host_for_paho, port_for_paho, username, password, command_topic):
                        if mqtt_connected.wait(timeout=10):
                            log_info("MQTT connection established.")
                            # Status will be set to IDLE by on_connect, which then may trigger pattern flight
                            if not telemetry_thread or not telemetry_thread.is_alive():
                                telemetry_thread = threading.Thread(target=publish_telemetry_periodically, args=(telemetry_topic,), daemon=True)
                                telemetry_thread.start()
                        else:
                            log_error("Timeout for MQTT on_connect. Retrying connection.")
                            if mqtt_client: mqtt_client.loop_stop(); mqtt_client.disconnect()
                            time.sleep(5) 
                    else:
                        log_error(f"Failed to initiate MQTT connection, retrying in {ARGS.poll_interval}s...")
                        time.sleep(ARGS.poll_interval)
                else:
                    log_error("No MQTT credentials. Reverting to PENDING_APPROVAL.")
                    current_flight_status = STATUS_PENDING_APPROVAL; time.sleep(ARGS.poll_interval)
            
            elif current_flight_status in [STATUS_IDLE, STATUS_TAKING_OFF, STATUS_FLYING, STATUS_PATTERN_FLYING, STATUS_LANDING, STATUS_RTL]:
                if not mqtt_connected.is_set():
                    log_warn("MQTT disconnected. Attempting reconnect...")
                    current_flight_status = STATUS_CONNECTING_MQTT 
                    if mqtt_client: 
                        try: mqtt_client.loop_stop(force=True) 
                        except: pass
                    time.sleep(5) 
                else:
                    time.sleep(0.1) # Active states, main loop just keeps alive

            elif current_flight_status == STATUS_ERROR:
                log_error("Drone in ERROR state. Attempting re-registration after delay.")
                time.sleep(30); current_flight_status = STATUS_REGISTERING
            else: 
                log_error(f"Unhandled status: {current_flight_status}. Resetting.")
                current_flight_status = STATUS_IDLE; time.sleep(5)

    except KeyboardInterrupt:
        log_info("Shutdown signal (KeyboardInterrupt).")
    finally:
        stop_event.set()
        if mqtt_client: log_info("Disconnecting MQTT..."); mqtt_client.loop_stop(); mqtt_client.disconnect()
        if telemetry_thread and telemetry_thread.is_alive(): log_info("Waiting for telemetry thread..."); telemetry_thread.join(timeout=2)
        log_info("Drone pattern simulator shut down.") 