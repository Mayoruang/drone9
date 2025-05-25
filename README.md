# 🚁 Drone9 - Advanced Drone Management System

[![GitHub license](https://img.shields.io/github/license/Mayoruang/drone9)](https://github.com/Mayoruang/drone9/blob/main/LICENSE)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Version](https://img.shields.io/badge/version-1.0.0-blue)](#)

A comprehensive, enterprise-grade drone management system featuring real-time monitoring, geofence management, automated registration workflows, and MQTT-based communication protocols.

## 🌟 Key Features

### 🛡️ **Geofence Management**
- Interactive map-based geofence creation and editing
- Support for multiple geofence types (No-Fly Zones, Restricted Areas, Flight Zones)
- Altitude-based restrictions and time-limited geofences
- Real-time thumbnail generation using Baidu Maps API
- Advanced violation detection and reporting

### 🚁 **Drone Registration & Management**
- Streamlined drone registration workflow
- Administrator approval/rejection system
- Automated MQTT credential provisioning
- Real-time status monitoring and health checks
- WebSocket-based live updates

### 📊 **Real-Time Monitoring**
- Live telemetry data visualization
- Interactive drone tracking on maps
- Historical flight path analysis
- System health and performance metrics
- Alert and notification system

### 🔄 **MQTT Communication**
- Secure, scalable MQTT message broker integration
- Bi-directional command and control capabilities
- Real-time telemetry data streaming
- Automatic connection management and recovery

## 🏗️ System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Vue.js SPA    │    │  Spring Boot    │    │   PostgreSQL    │
│   (Frontend)    │◄──►│   (Backend)     │◄──►│   (Database)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │                          
                              ▼                          
                    ┌─────────────────┐    ┌─────────────────┐
                    │      EMQX       │    │    InfluxDB     │
                    │ (MQTT Broker)   │    │ (Time-Series)   │
                    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Drone Fleet    │
                    │  (Simulators)   │
                    └─────────────────┘
```

## 🛠️ Technologies

### Backend
- **Framework**: Spring Boot 3.2+
- **Security**: Spring Security with JWT
- **Database**: PostgreSQL 15+ (Relational), InfluxDB 2.0+ (Time-series)
- **Messaging**: EMQX MQTT Broker
- **ORM**: Spring Data JPA with Hibernate
- **Real-time**: WebSocket, MQTT

### Frontend
- **Framework**: Vue.js 3 with Composition API
- **UI Library**: Ant Design Vue 4.0+
- **Build Tool**: Vite
- **State Management**: Pinia
- **Maps**: Baidu Maps API
- **Admin Template**: Vue-Vben-Admin

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **Database**: PostgreSQL, InfluxDB
- **Message Broker**: EMQX
- **Maps**: Baidu Maps Static API

## 🚀 Quick Start

### Prerequisites
- **Java**: 17 or higher
- **Node.js**: 18 or higher
- **Docker**: 20.10 or higher
- **Python**: 3.8+ (for drone simulators)

### 1. Clone Repository
```bash
git clone https://github.com/Mayoruang/drone9.git
cd drone9
```

### 2. Start Infrastructure Services
```bash
# Start PostgreSQL, InfluxDB, and EMQX
docker-compose up -d postgres influxdb emqx
```

### 3. Start Backend Service
```bash
cd backend
./mvnw spring-boot:run
```

The backend will be available at `http://localhost:8080`

### 4. Start Frontend Application
```bash
cd vue-vben-admin/apps/web-antd
npm install
npm run dev
```

The frontend will be available at `http://localhost:5173`

### 5. Run Drone Simulators (Optional)
```bash
# Install Python dependencies
pip install -r requirements.txt

# Start drone simulators
python smart_drone_simulator.py --drones 5
```

## 📱 Application Access

- **Admin Dashboard**: `http://localhost:5173`
- **API Documentation**: `http://localhost:8080/swagger-ui.html`
- **EMQX Dashboard**: `http://localhost:18083` (admin/public)
- **Database**: PostgreSQL on `localhost:5432`
- **InfluxDB**: `http://localhost:8086`

## 📖 Detailed Setup Guide

### Environment Configuration

1. **Database Setup**: PostgreSQL will auto-initialize with required schemas
2. **EMQX Configuration**: MQTT broker configured for drone authentication
3. **InfluxDB**: Time-series database for telemetry data
4. **Baidu Maps**: Configure API key for map services (optional)

### Configuration Files

- `backend/src/main/resources/application.yml` - Backend configuration
- `vue-vben-admin/apps/web-antd/.env.local` - Frontend environment variables
- `docker-compose.yml` - Infrastructure services configuration

### Sample Environment Variables

```bash
# Backend (application.yml)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/drone_management
    username: postgres
    password: password

# MQTT Configuration
mqtt:
  broker: tcp://localhost:1883
  username: admin
  password: public

# Frontend (.env.local)
VITE_GLOB_API_URL=http://localhost:8080/api
VITE_GLOB_APP_TITLE=Drone Management System
```

## 🔧 Development Features

### Drone Simulator
The system includes a sophisticated drone simulator that can:

- Simulate multiple drones simultaneously
- Generate realistic flight patterns
- Send telemetry data via MQTT
- Respond to remote commands
- Simulate various flight scenarios

```bash
# Run simulator with custom parameters
python smart_drone_simulator.py \
  --drones 10 \
  --backend http://localhost:8080/api/v1 \
  --mqtt-host localhost \
  --mqtt-port 1883 \
  --log-level DEBUG
```

### API Testing
Use the included test utilities:

- `test-api.html` - Interactive API testing interface
- `vue-vben-admin/apps/web-antd/test-geofence.html` - Geofence API testing

## 🌐 Core Workflows

### 1. Drone Registration Process
1. Drone submits registration request via API
2. Admin reviews and approves/rejects in dashboard
3. System generates MQTT credentials
4. Drone connects to MQTT broker
5. Real-time monitoring begins

### 2. Geofence Management
1. Admin creates geofences using map interface
2. System generates map thumbnails
3. Geofences are enforced in real-time
4. Violations are detected and reported
5. Alerts are sent to relevant personnel

### 3. Mission Monitoring
1. Drones send telemetry data via MQTT
2. System stores data in InfluxDB
3. Real-time visualization on dashboard
4. Historical analysis and reporting

## 📊 Data Management

### Database Schema
- **PostgreSQL**: Stores drone registrations, user accounts, geofences, and system configuration
- **InfluxDB**: Stores time-series telemetry data, flight paths, and performance metrics

### Key Data Models
- `Drone`: Core drone information and credentials
- `Geofence`: Geographical boundaries and restrictions  
- `DroneRegistrationRequest`: Registration workflow management
- `TelemetryData`: Real-time drone status and position
- `GeofenceViolation`: Security and compliance tracking

## 🛡️ Security Features

- **JWT Authentication**: Secure API access
- **MQTT Security**: Individual drone credentials
- **Role-Based Access**: Admin and operator permissions
- **Geofence Enforcement**: Automated boundary checking
- **Audit Logging**: Complete activity tracking

## 🔧 Troubleshooting

### Common Issues

1. **Database Connection Errors**
   ```bash
   # Check if PostgreSQL is running
   docker ps | grep postgres
   
   # Reset database
   docker-compose down postgres
   docker-compose up -d postgres
   ```

2. **MQTT Connection Issues**
   ```bash
   # Check EMQX status
   docker logs emqx
   
   # Test MQTT connection
   ./check_mqtt_config.sh
   ```

3. **Frontend Build Errors**
   ```bash
   # Clear node modules and reinstall
   cd vue-vben-admin/apps/web-antd
   rm -rf node_modules package-lock.json
   npm install
   ```

### Performance Optimization

- **Database Indexing**: Optimize queries with proper indexing
- **MQTT Tuning**: Configure QoS levels and connection pools
- **Frontend Caching**: Implement efficient data caching strategies
- **Time-series Optimization**: Configure InfluxDB retention policies

## 📝 API Documentation

Comprehensive API documentation is available at:
- **Interactive Docs**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`
- **Detailed Docs**: See `API_DOCUMENTATION.md`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature-name`
3. Commit changes: `git commit -am 'Add feature'`
4. Push to branch: `git push origin feature-name`
5. Submit a pull request

### Development Guidelines
- Follow Spring Boot best practices for backend development
- Use Vue.js Composition API for frontend components
- Write comprehensive tests for new features
- Update documentation for API changes

## 📄 License

This project is proprietary software. All rights reserved.

## 🏗️ Future Roadmap

- [ ] **Advanced Analytics**: ML-based flight pattern analysis
- [ ] **Mobile App**: Native iOS/Android applications  
- [ ] **Multi-tenant Support**: Enterprise customer isolation
- [ ] **Advanced Geofencing**: 3D geofences and dynamic boundaries
- [ ] **Fleet Management**: Automated mission planning and scheduling
- [ ] **Integration APIs**: Third-party system integrations

## 📞 Support

For technical support or questions:
- **Issues**: [GitHub Issues](https://github.com/Mayoruang/drone9/issues)
- **Documentation**: See `/docs` directory
- **Email**: Contact system administrators

---

**Built with ❤️ for next-generation drone management** 