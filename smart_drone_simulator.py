import argparse
import json
import random
import time
import uuid
import threading
import requests
import paho.mqtt.client as mqtt
import math # For haversine/coordinate calculations

# --- Configuration & Constants ---
DEFAULT_BACKEND_URL = "http://localhost:8080/api/v1"
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_MODEL = "SimDrone-X2"
DEFAULT_TELEMETRY_INTERVAL_SEC = 5
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
STATUS_ONLINE = "ONLINE" # Connected to MQTT, idle
STATUS_FLYING = "FLYING"
STATUS_LANDING = "LANDING"
STATUS_RTL = "RETURNING_TO_LAUNCH" # Return to Launch
STATUS_ERROR = "ERROR"
STATUS_PATTERN_FLYING = "PATTERN_FLYING"

# Earth radius in meters (for rough distance calculations)
EARTH_RADIUS_METERS = 6371000

# --- Global Variables ---
drone_id_internal = None # System-wide UUID for the drone, received after approval
mqtt_client = None
mqtt_connected = threading.Event()
stop_event = threading.Event()

# Drone state
current_latitude = DEFAULT_INITIAL_LATITUDE
current_longitude = DEFAULT_INITIAL_LONGITUDE
current_altitude = DEFAULT_INITIAL_ALTITUDE
current_battery = DEFAULT_BATTERY_LEVEL
current_flight_status = STATUS_IDLE
current_speed = 0.0

# Target state for GOTO command
target_latitude = None
target_longitude = None
target_altitude = None

# New for pattern flight
waypoints = []
current_waypoint_index = 0
pattern_loop = True # Whether to loop the pattern or stop after one cycle

def get_serial_number(provided_serial):
    if provided_serial:
        return provided_serial
    return f"SIM-{str(uuid.uuid4())[:12].upper()}"

def log_info(message):
    print(f"[*] INFO: {message}")

def log_error(message):
    print(f"[!] ERROR: {message}")

def log_debug(message):
    if ARGS.verbose:
        print(f"[D] DEBUG: {message}")

# --- API Interaction ---
def register_drone(serial_number, model, backend_url):
    log_info(f"Attempting registration for S/N: {serial_number}, Model: {model}")
    payload = {
        "serialNumber": serial_number,
        "model": model,
        "notes": "Simulated drone registration"
    }
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
    global current_flight_status, drone_id_internal
    if rc == 0:
        log_info(f"Successfully connected to MQTT broker. Drone ID: {drone_id_internal}")
        mqtt_connected.set()
        command_topic = userdata.get("command_topic")
        if command_topic:
            client.subscribe(command_topic)
            log_info(f"Subscribed to command topic: {command_topic}")
        else:
            log_error("Command topic not found in userdata for MQTT subscription.")
        # Keep the drone in ground idle status once connected
        current_flight_status = STATUS_IDLE
    else:
        log_error(f"MQTT connection failed with code {rc}")
        current_flight_status = STATUS_ERROR
        mqtt_connected.clear() # Ensure it's clear if connection fails

def on_disconnect(client, userdata, rc, properties=None):
    global current_flight_status
    log_info(f"Disconnected from MQTT broker (rc: {rc}).")
    mqtt_connected.clear()
    current_flight_status = STATUS_IDLE # Or some other offline status

def on_message(client, userdata, msg):
    global current_flight_status, target_latitude, target_longitude, target_altitude, current_speed
    payload_str = msg.payload.decode()
    log_info(f"Received command on topic '{msg.topic}': {payload_str}")
    try:
        command_data = json.loads(payload_str)
        action = command_data.get("type") # Assuming 'type' based on DroneCommand.java
        parameters = command_data.get("parameters", {})

        if action == "GOTO" or action == "GOTO_WAYPOINT": # Matching design doc
            target_latitude = parameters.get("latitude", current_latitude)
            target_longitude = parameters.get("longitude", current_longitude)
            target_altitude = parameters.get("altitude", current_altitude)
            # speed_param = parameters.get("speed", DEFAULT_SPEED) # System to UAV command has speed
            # current_speed = speed_param if speed_param is not None else DEFAULT_SPEED
            current_speed = DEFAULT_SPEED # Simplified for now
            current_flight_status = STATUS_FLYING
            log_info(f"Executing GOTO: Lat={target_latitude}, Lon={target_longitude}, Alt={target_altitude}, Speed={current_speed}")
        elif action == "LAND":
            current_flight_status = STATUS_LANDING
            current_speed = 1.0 # Slow down for landing
            log_info("Executing LAND")
        elif action == "RTL": # Return To Launch
            current_flight_status = STATUS_RTL
            # For simulation, just set a target that's near origin or a fixed point
            target_latitude = DEFAULT_INITIAL_LATITUDE + 0.0001 # Slight offset
            target_longitude = DEFAULT_INITIAL_LONGITUDE + 0.0001
            target_altitude = DEFAULT_INITIAL_ALTITUDE
            current_speed = DEFAULT_SPEED * 0.8 # Slower for RTL
            log_info("Executing RTL")
        elif action == "TAKEOFF":
            target_altitude = parameters.get("altitude", current_altitude + 10) # Default takeoff 10m higher
            current_flight_status = STATUS_FLYING
            current_speed = 2.0 # Takeoff speed
            log_info(f"Executing TAKEOFF to altitude: {target_altitude}")
        else:
            log_warn(f"Unknown command action: {action}")

        # Acknowledge command (optional, as per design doc)
        # ack_payload = {"command_id": command_data.get("commandId"), "status": "RECEIVED"}
        # ack_topic = f"drones/{drone_id_internal}/command_ack"
        # client.publish(ack_topic, json.dumps(ack_payload))
        # log_debug(f"Sent ACK for command {command_data.get('commandId')} to {ack_topic}")

    except json.JSONDecodeError:
        log_error(f"Failed to parse command JSON: {payload_str}")
    except Exception as e:
        log_error(f"Error processing command: {e}")


def connect_mqtt(host, port, username, password, command_topic):
    global mqtt_client
    client_id = f"sim-drone-{serial_number}-{str(uuid.uuid4())[:4]}"
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
        mqtt_client.loop_start() # Start a background thread for network traffic, callbacks
        return True
    except Exception as e:
        log_error(f"MQTT connection setup failed: {e}")
        return False

# --- Drone Simulation Logic ---
def calculate_new_coords(lat, lon, bearing_deg, distance_m):
    """
    Calculate new lat/lon given a starting point, bearing, and distance.
    Simplified version, assumes relatively small distances.
    Bearing: 0=North, 90=East, 180=South, 270=West
    """
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

def generate_rectangle_waypoints(start_lat, start_lon, width_m, height_m, altitude_m):
    """Generates waypoints for a rectangle pattern."""
    global waypoints, current_waypoint_index
    waypoints = []
    current_waypoint_index = 0

    p1 = (start_lat, start_lon, altitude_m)
    waypoints.append(p1)

    # East (width)
    p2_lat, p2_lon = calculate_new_coords(p1[0], p1[1], 90, width_m)
    waypoints.append((p2_lat, p2_lon, altitude_m))

    # North (height) from p2
    p3_lat, p3_lon = calculate_new_coords(p2_lat, p2_lon, 0, height_m)
    waypoints.append((p3_lat, p3_lon, altitude_m))
    
    # West (width) from p3
    p4_lat, p4_lon = calculate_new_coords(p3_lat, p3_lon, 270, width_m)
    waypoints.append((p4_lat, p4_lon, altitude_m))

    # South (height) from p4 (back to start_lat, effectively closing to p1's longitude)
    # Or simply add the start point again if loop is true to ensure closing the loop for display
    # For simplicity, let's just add the start again for looping.
    # A more robust approach would ensure p4 is (p1_lat, p3_lon)
    # and then the final leg goes from p4 to p1.

    if pattern_loop:
        waypoints.append(p1) # Loop back to start

    log_info(f"Generated rectangle waypoints: {waypoints}")
    return waypoints

def start_pattern_flight():
    global current_flight_status, target_latitude, target_longitude, target_altitude, current_speed, waypoints, current_waypoint_index
    if not waypoints:
        log_warn("No waypoints generated for pattern flight.")
        current_flight_status = STATUS_IDLE # Fallback
        return

    current_waypoint_index = 0
    next_wp = waypoints[current_waypoint_index]
    target_latitude, target_longitude, target_altitude = next_wp
    current_flight_status = STATUS_PATTERN_FLYING
    current_speed = ARGS.flight_speed # Use speed from args
    log_info(f"Starting pattern flight. First waypoint: {next_wp}. Speed: {current_speed} m/s")

def simulate_movement_and_battery():
    global current_latitude, current_longitude, current_altitude, current_battery, current_flight_status
    global current_speed, target_latitude, target_longitude, target_altitude
    global waypoints, current_waypoint_index

    # Battery drain (same as before, but include STATUS_PATTERN_FLYING)
    if current_flight_status not in [STATUS_IDLE, STATUS_PENDING_APPROVAL, STATUS_REGISTERING]:
        current_battery -= 0.05 # Adjusted drain rate slightly
        if ARGS.flight_pattern != "NONE": # More drain if actively pattern flying
             current_battery -= 0.05
    if current_battery < 0:
        current_battery = 0
        if current_flight_status != STATUS_LANDING:
            log_warn("Battery depleted! Forcing LAND.")
            current_flight_status = STATUS_LANDING
            waypoints = [] # Clear waypoints if battery dies

    # Movement simulation
    if current_flight_status == STATUS_FLYING or current_flight_status == STATUS_RTL or current_flight_status == STATUS_PATTERN_FLYING:
        if target_latitude is not None and target_longitude is not None and target_altitude is not None:
            lat_diff = target_latitude - current_latitude
            lon_diff = target_longitude - current_longitude
            alt_diff = target_altitude - current_altitude

            # Using Haversine for ground distance for better accuracy over larger distances
            # For altitude, simple difference is fine
            dlon = math.radians(lon_diff)
            dlat = math.radians(lat_diff)
            a = math.sin(dlat / 2)**2 + math.cos(math.radians(current_latitude)) * math.cos(math.radians(target_latitude)) * math.sin(dlon / 2)**2
            c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
            ground_distance = EARTH_RADIUS_METERS * c
            
            total_distance = (ground_distance**2 + alt_diff**2)**0.5 # 3D distance
            
            time_step = ARGS.telemetry_interval

            if total_distance > 0.1: # If not at target (increased threshold slightly)
                # Move a fraction of the distance based on speed
                move_distance_this_step = current_speed * time_step
                
                # If move_distance_this_step is greater than remaining distance, just move to target
                if move_distance_this_step >= total_distance:
                    move_fraction = 1.0
                else:
                    move_fraction = move_distance_this_step / total_distance

                current_latitude += lat_diff * move_fraction
                current_longitude += lon_diff * move_fraction
                current_altitude += alt_diff * move_fraction
                log_debug(f"Moving ({current_flight_status}): Lat={current_latitude:.6f}, Lon={current_longitude:.6f}, Alt={current_altitude:.1f}m. Target WP: {current_waypoint_index if waypoints else 'N/A'}")
            else: # Arrived at target
                if current_flight_status == STATUS_PATTERN_FLYING:
                    log_info(f"Arrived at waypoint {current_waypoint_index + 1}/{len(waypoints)}: ({target_latitude:.6f}, {target_longitude:.6f})")
                    current_waypoint_index += 1
                    if current_waypoint_index < len(waypoints):
                        next_wp = waypoints[current_waypoint_index]
                        target_latitude, target_longitude, target_altitude = next_wp
                        log_info(f"Proceeding to next waypoint {current_waypoint_index + 1}: ({target_latitude:.6f}, {target_longitude:.6f})")
                    else: # Pattern complete
                        if pattern_loop and ARGS.flight_pattern != "NONE": # Check if pattern was specified for looping
                            log_info("Pattern complete. Looping...")
                            current_waypoint_index = 0
                            next_wp = waypoints[current_waypoint_index]
                            target_latitude, target_longitude, target_altitude = next_wp
                        else:
                            log_info("Pattern complete. Hovering at last waypoint.")
                            current_flight_status = STATUS_IDLE # Or hover
                            current_speed = 0.0
                            waypoints = [] # Clear waypoints
                elif current_flight_status == STATUS_RTL:
                    log_info(f"Arrived at RTL target. Lat={target_latitude}, Lon={target_longitude}")
                    current_flight_status = STATUS_LANDING
                    current_speed = 1.0
                else: # GOTO complete
                    log_info(f"Arrived at GOTO target. Lat={target_latitude}, Lon={target_longitude}")
                    current_flight_status = STATUS_IDLE
                    current_speed = 0.0

    elif current_flight_status == STATUS_LANDING:
        if current_altitude > 0.5:
            current_altitude -= 1.0 
            current_speed = 0.5 
            log_debug(f"Landing: Alt={current_altitude:.1f}m")
        else:
            current_altitude = 0.0
            current_flight_status = STATUS_IDLE # Landed, now idle
            current_speed = 0.0
            log_info("Landed successfully.")

def publish_telemetry_periodically(telemetry_topic):
    global mqtt_client
    while not stop_event.is_set():
        if mqtt_connected.is_set() and mqtt_client:
            simulate_movement_and_battery()

            payload = {
                "timestamp": time.time(), # Using epoch float, can be ISO8601 string too
                "latitude": round(current_latitude, 6),
                "longitude": round(current_longitude, 6),
                "altitude": round(current_altitude, 2),
                "batteryLevel": round(current_battery, 1),
                "status": current_flight_status,
                "speed": round(current_speed, 2),
                "droneId": drone_id_internal # Use camelCase to match backend DTO
            }
            try:
                json_payload = json.dumps(payload)
                result = mqtt_client.publish(telemetry_topic, json_payload)
                result.wait_for_publish(timeout=2) # Wait for publish to complete
                if result.is_published():
                    log_debug(f"Published telemetry to '{telemetry_topic}': {json_payload}")
                else:
                    log_warn(f"Failed to publish telemetry to '{telemetry_topic}' (rc: {result.rc})")

            except Exception as e:
                log_error(f"Error publishing telemetry: {e}")
        
        sleep_duration = ARGS.telemetry_interval
        # If not connected, we might want to sleep less to retry connection faster,
        # but the main loop handles connection retries. Telemetry only makes sense when connected.
        time.sleep(sleep_duration)


# --- Main ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Drone Simulator Agent with Pattern Flight")
    parser.add_argument("--serial_number", type=str, help="Drone serial number (default: random)")
    parser.add_argument("--model", type=str, default=DEFAULT_MODEL, help="Drone model")
    parser.add_argument("--backend_url", type=str, default=DEFAULT_BACKEND_URL, help="Backend API URL")
    parser.add_argument("--mqtt_host", type=str, default=DEFAULT_MQTT_HOST, help="MQTT broker host")
    parser.add_argument("--mqtt_port", type=int, default=DEFAULT_MQTT_PORT, help="MQTT broker port")
    parser.add_argument("--telemetry_interval", type=float, default=DEFAULT_TELEMETRY_INTERVAL_SEC, help="Telemetry publishing interval (seconds)")
    parser.add_argument("--poll_interval", type=float, default=DEFAULT_POLL_INTERVAL_SEC, help="Registration status polling interval (seconds)")
    parser.add_argument("--lat", type=float, default=DEFAULT_INITIAL_LATITUDE, help="Initial latitude")
    parser.add_argument("--lon", type=float, default=DEFAULT_INITIAL_LONGITUDE, help="Initial longitude")
    parser.add_argument("--alt", type=float, default=DEFAULT_INITIAL_ALTITUDE, help="Initial altitude (meters)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose debug logging")
    parser.add_argument("--flight_pattern", type=str, default="NONE", choices=["NONE", "RECTANGLE"], help="Flight pattern to execute after MQTT connection (NONE or RECTANGLE)")
    parser.add_argument("--rectangle_lat_start", type=float, default=DEFAULT_INITIAL_LATITUDE, help="Rectangle pattern: starting latitude")
    parser.add_argument("--rectangle_lon_start", type=float, default=DEFAULT_INITIAL_LONGITUDE, help="Rectangle pattern: starting longitude")
    parser.add_argument("--rectangle_width", type=float, default=100.0, help="Rectangle pattern: width in meters (East-West)")
    parser.add_argument("--rectangle_height", type=float, default=50.0, help="Rectangle pattern: height in meters (North-South)")
    parser.add_argument("--flight_altitude", type=float, default=20.0, help="Altitude for pattern flight (meters)")
    parser.add_argument("--flight_speed", type=float, default=DEFAULT_SPEED, help="Speed for pattern flight (m/s)")
    parser.add_argument("--pattern_loop", type=lambda x: (str(x).lower() == 'true'), default=True, help="Loop the flight pattern (True/False)")

    ARGS = parser.parse_args()

    serial_number = get_serial_number(ARGS.serial_number)
    current_latitude = ARGS.lat
    current_longitude = ARGS.lon
    current_altitude = ARGS.alt

    log_info(f"Starting Drone Simulator: S/N={serial_number}, Model={ARGS.model}")
    log_info(f"Backend: {ARGS.backend_url}, MQTT: {ARGS.mqtt_host}:{ARGS.mqtt_port}")
    log_info(f"Telemetry Interval: {ARGS.telemetry_interval}s, Poll Interval: {ARGS.poll_interval}s")

    telemetry_thread = None
    request_id = None
    mqtt_credentials = None
    current_flight_status = STATUS_REGISTERING

    try:
        while not stop_event.is_set():
            if current_flight_status == STATUS_REGISTERING:
                request_id = register_drone(serial_number, ARGS.model, ARGS.backend_url)
                if request_id:
                    current_flight_status = STATUS_PENDING_APPROVAL
                else:
                    log_info(f"Registration failed, retrying in {ARGS.poll_interval}s...")
                    time.sleep(ARGS.poll_interval) # Wait before retrying registration
            
            elif current_flight_status == STATUS_PENDING_APPROVAL:
                if not request_id: # Should not happen if logic is correct
                    log_error("No request_id to poll status, attempting re-registration.")
                    current_flight_status = STATUS_REGISTERING
                    time.sleep(1)
                    continue

                status_data = check_registration_status(request_id, ARGS.backend_url)
                if status_data:
                    reg_status = status_data.get("status")
                    log_info(f"Current registration status: {reg_status}")
                    if reg_status == "APPROVED":
                        mqtt_credentials = status_data.get("mqttCredentials")
                        drone_id_internal = status_data.get("droneId") # Capture the system drone_id
                        if not drone_id_internal: # Fallback if not in top level, check inside credentials
                             if mqtt_credentials and mqtt_credentials.get("droneId"): # Some systems might put it here
                                drone_id_internal = mqtt_credentials.get("droneId")
                        
                        if not drone_id_internal: # If still not found, try request's droneId field
                            drone_id_internal = status_data.get("droneIdFromRequest") # Hypothetical field, adjust if needed


                        if mqtt_credentials and drone_id_internal:
                            log_info(f"Registration APPROVED for Drone ID: {drone_id_internal}!")
                            log_info(f"MQTT Credentials: {mqtt_credentials}")
                            current_flight_status = STATUS_CONNECTING_MQTT
                        else:
                            log_error("Approved, but MQTT credentials or Drone ID missing in response. Waiting...")
                    elif reg_status == "REJECTED":
                        log_error(f"Registration REJECTED. Reason: {status_data.get('message', 'N/A')}. Exiting.")
                        stop_event.set()
                        break 
                    # Else (PENDING_APPROVAL), continue polling
                else:
                    log_warn("Failed to get registration status, will retry.")
                time.sleep(ARGS.poll_interval)

            elif current_flight_status == STATUS_CONNECTING_MQTT:
                if mqtt_credentials:
                    # Construct broker_url from host and port if not fully provided by backend
                    # Assuming backend gives full URL like "tcp://host:port"
                    # If backend gives "host:port", then:
                    # broker_connect_url = f"tcp://{mqtt_credentials.get('mqttBrokerUrl', f'{ARGS.mqtt_host}:{ARGS.mqtt_port}')}"
                    
                    broker_connect_url = mqtt_credentials.get('mqttBrokerUrl')
                    if not broker_connect_url.startswith("tcp://") and not broker_connect_url.startswith("ws://"):
                         # if backend provides just host:port or just host
                        if ":" in broker_connect_url:
                             host, port = broker_connect_url.split(":")
                        else: # just host
                             host = broker_connect_url
                             port = ARGS.mqtt_port # use default or cmd line arg
                        broker_actual_url_for_paho = host
                        broker_actual_port_for_paho = int(port)

                    else: # Backend provides full tcp://host:port or ws://host:port
                        # Paho client's connect() method takes host and port separately
                        url_parts = broker_connect_url.replace("tcp://", "").replace("ws://", "").split(":")
                        broker_actual_url_for_paho = url_parts[0]
                        broker_actual_port_for_paho = int(url_parts[1]) if len(url_parts) > 1 else ARGS.mqtt_port


                    username = mqtt_credentials.get("mqttUsername")
                    password = mqtt_credentials.get("mqttPassword") # In a real scenario, this might be a temporary token
                    command_topic = mqtt_credentials.get("mqttTopicCommands")
                    telemetry_topic = mqtt_credentials.get("mqttTopicTelemetry")

                    if not all([broker_actual_url_for_paho, username, command_topic, telemetry_topic]):
                        log_error("Incomplete MQTT credentials received. Cannot connect. Retrying poll.")
                        current_flight_status = STATUS_PENDING_APPROVAL # Go back to polling
                        mqtt_credentials = None # Clear to force re-fetch on next poll
                        time.sleep(ARGS.poll_interval)
                        continue
                    
                    # Use paho's host/port args for connect()
                    # The 'broker_url' parameter for connect_mqtt should be just the host for Paho
                    if connect_mqtt(broker_actual_url_for_paho, broker_actual_port_for_paho, username, password, command_topic):
                        # Wait for on_connect to set mqtt_connected
                        if mqtt_connected.wait(timeout=10): # Wait up to 10s for connection
                            log_info("MQTT connection established and callback received.")
                            current_flight_status = STATUS_IDLE
                            telemetry_thread = threading.Thread(target=publish_telemetry_periodically, args=(telemetry_topic,), daemon=True)
                            telemetry_thread.start()
                        else:
                            log_error("Timeout waiting for MQTT on_connect callback. Will retry connection.")
                            if mqtt_client:
                                mqtt_client.loop_stop()
                                mqtt_client.disconnect()
                            # Fall back to re-attempting connection, or even re-polling status
                            time.sleep(5) # Brief pause before retry
                    else:
                        log_error(f"Failed to initiate MQTT connection, retrying in {ARGS.poll_interval}s...")
                        time.sleep(ARGS.poll_interval)
                else:
                    log_error("No MQTT credentials available. Reverting to PENDING_APPROVAL.")
                    current_flight_status = STATUS_PENDING_APPROVAL
                    time.sleep(ARGS.poll_interval)
            
            elif current_flight_status == STATUS_IDLE and ARGS.flight_pattern != "NONE" and not waypoints: # Connected and ready for pattern
                if ARGS.flight_pattern == "RECTANGLE":
                    log_info("Preparing RECTANGLE flight pattern...")
                    initial_pattern_altitude = ARGS.flight_altitude if ARGS.flight_altitude > 0 else current_altitude
                    # If drone is on ground (alt=0), and flight_altitude is also 0, use a default takeoff alt
                    if initial_pattern_altitude == 0 and current_altitude == 0:
                        initial_pattern_altitude = 10.0 # Default takeoff to 10m then fly pattern at ARGS.flight_altitude
                        log_info(f"Drone on ground, pattern altitude also 0. Taking off to {initial_pattern_altitude}m first.")
                        # This implies a takeoff phase before starting the pattern if not already airborne
                        # For simplicity, we'll assume pattern starts at specified flight_altitude directly
                        # or current altitude if already flying.
                    
                    # For rectangle pattern, the start lat/lon for pattern might be different from drone's current initial
                    # Or, we use drone's current initial lat/lon as the start of the rectangle.
                    # Let's use the specific args for rectangle start, and flight_altitude for all waypoints.
                    generate_rectangle_waypoints(
                        ARGS.rectangle_lat_start, 
                        ARGS.rectangle_lon_start, 
                        ARGS.rectangle_width, 
                        ARGS.rectangle_height, 
                        ARGS.flight_altitude
                    )
                    # Simulate takeoff to pattern altitude if on ground
                    if current_altitude < ARGS.flight_altitude:
                        log_info(f"Drone on ground or below pattern altitude. Simulating takeoff to {ARGS.flight_altitude}m.")
                        target_altitude = ARGS.flight_altitude
                        target_latitude = current_latitude # Stay in place for takeoff
                        target_longitude = current_longitude
                        current_speed = 2.0 # Takeoff speed
                        current_flight_status = STATUS_FLYING # General flying state for takeoff
                        # Once takeoff altitude is reached, simulate_movement will handle transition or
                        # we need a specific check here to then call start_pattern_flight().
                        # For now, let's assume it takes off and then pattern starts
                        # A better way is a state like STATUS_TAKING_OFF_FOR_PATTERN
                    else: # Already at or above pattern altitude
                        start_pattern_flight()

            elif current_flight_status == STATUS_FLYING and waypoints: # Could be GOTO or takeoff for pattern
                # If it was taking off for a pattern, and altitude is reached
                if target_altitude is not None and abs(current_altitude - target_altitude) < 0.5 and not (current_flight_status == STATUS_PATTERN_FLYING) and ARGS.flight_pattern != "NONE":
                    # This condition is a bit tricky. We need to ensure this only triggers after a "takeoff for pattern"
                    # and not during a regular GOTO.
                    # A dedicated state like STATUS_TAKING_OFF_FOR_PATTERN would be cleaner.
                    # For now, if waypoints are present, and we just reached target_alt from a non-PATTERN_FLYING state:
                    log_info(f"Reached takeoff altitude for pattern. Starting pattern flight.")
                    start_pattern_flight()

            elif current_flight_status in [STATUS_ONLINE, STATUS_FLYING, STATUS_LANDING, STATUS_RTL]:
                if not mqtt_connected.is_set():
                    log_warn("MQTT disconnected while operational. Attempting to reconnect...")
                    current_flight_status = STATUS_CONNECTING_MQTT 
                    if mqtt_client:
                        try:
                            mqtt_client.loop_stop(force=True) 
                        except:
                            pass
                    time.sleep(5) 
                else:
                    time.sleep(0.1) # Sleep less if actively flying pattern for faster updates

            elif current_flight_status == STATUS_ERROR:
                log_error("Drone in ERROR state. Pausing for a while before trying to recover or exit.")
                time.sleep(30)
                # Basic recovery: try to re-register. More sophisticated recovery might be needed.
                log_info("Attempting to recover by re-registering.")
                current_flight_status = STATUS_REGISTERING

            else: # Catch any unexpected status
                log_error(f"Unhandled drone status: {current_flight_status}. Resetting to IDLE/REGISTERING.")
                current_flight_status = STATUS_IDLE # or STATUS_REGISTERING
                time.sleep(5)

    except KeyboardInterrupt:
        log_info("Shutdown signal received (KeyboardInterrupt).")
    finally:
        stop_event.set()
        if mqtt_client:
            log_info("Disconnecting MQTT client...")
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
        if telemetry_thread and telemetry_thread.is_alive():
            log_info("Waiting for telemetry thread to finish...")
            telemetry_thread.join(timeout=5)
        log_info("Drone pattern simulator shut down.") 