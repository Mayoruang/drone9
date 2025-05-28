#!/bin/bash

# 智能系统启动脚本
# 自动检测网络变化并启动无人机管理系统

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "${PURPLE}[SYSTEM]${NC} $1"
}

# 检查系统依赖
check_dependencies() {
    log_info "检查系统依赖..."
    
    local missing_deps=()
    
    # 检查Docker
    if ! command -v docker >/dev/null 2>&1; then
        missing_deps+=("Docker")
    fi
    
    # 检查Docker Compose
    if ! command -v docker-compose >/dev/null 2>&1; then
        missing_deps+=("Docker Compose")
    fi
    
    # 检查Java
    if ! command -v java >/dev/null 2>&1; then
        missing_deps+=("Java")
    fi
    
    # 检查Node.js
    if ! command -v node >/dev/null 2>&1; then
        missing_deps+=("Node.js")
    fi
    
    # 检查pnpm
    if ! command -v pnpm >/dev/null 2>&1; then
        missing_deps+=("pnpm")
    fi
    
    # 检查curl
    if ! command -v curl >/dev/null 2>&1; then
        missing_deps+=("curl")
    fi
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "缺少以下依赖: ${missing_deps[*]}"
        log_info "请先安装所需依赖再运行此脚本"
        exit 1
    fi
    
    log_success "系统依赖检查通过"
}

# 检查并启动Docker服务
check_docker_services() {
    log_info "检查Docker服务状态..."
    
    # 检查Docker是否运行
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker服务未运行，请先启动Docker"
        exit 1
    fi
    
    # 检查必需的Docker服务
    local required_services=("drone-postgres" "drone-influxdb" "drone-emqx")
    local missing_services=()
    
    for service in "${required_services[@]}"; do
        if ! docker ps --format "table {{.Names}}" | grep -q "^${service}$"; then
            missing_services+=("$service")
        fi
    done
    
    if [ ${#missing_services[@]} -ne 0 ]; then
        log_warning "以下Docker服务未运行: ${missing_services[*]}"
        log_info "启动Docker基础服务..."
        
        # 启动Docker服务
        docker-compose up -d postgres influxdb emqx
        
        # 等待服务启动
        log_info "等待Docker服务启动..."
        sleep 10
        
        # 验证服务启动
        for service in "${required_services[@]}"; do
            if docker ps --format "table {{.Names}}" | grep -q "^${service}$"; then
                log_success "Docker服务 $service 启动成功"
            else
                log_error "Docker服务 $service 启动失败"
                exit 1
            fi
        done
    else
        log_success "所有Docker服务正常运行"
    fi
    
    # 显示服务状态
    echo ""
    log_info "Docker服务状态:"
    docker-compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
    echo ""
}

# 检查项目文件
check_project_files() {
    log_info "检查项目文件..."
    
    local required_files=(
        "docker-compose.yml"
        "backend/mvnw"
        "vue-vben-admin/package.json"
        "backend/src/main/resources/application-external.yml"
        "update-network-config.sh"
    )
    
    for file in "${required_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            log_error "缺少必要文件: $file"
            exit 1
        fi
    done
    
    log_success "项目文件检查通过"
}

# 检查端口占用
check_ports() {
    log_info "检查端口占用..."
    
    local backend_port=8080
    local frontend_port=5666
    
    if lsof -Pi :$backend_port -sTCP:LISTEN -t >/dev/null 2>&1; then
        log_warning "端口 $backend_port 已被占用，将尝试停止相关进程"
        pkill -f "spring-boot:run" 2>/dev/null || true
        sleep 2
    fi
    
    if lsof -Pi :$frontend_port -sTCP:LISTEN -t >/dev/null 2>&1; then
        log_warning "端口 $frontend_port 已被占用，将尝试停止相关进程"
        pkill -f "vite.*5666" 2>/dev/null || true
        sleep 2
    fi
    
    log_success "端口检查完成"
}

# 编译后端项目
build_backend() {
    log_info "编译后端项目..."
    
    cd backend
    
    # 检查是否需要重新编译
    if [[ ! -f "target/classes/com/huang/backend/BackendApplication.class" ]] || 
       [[ "src/main/java" -nt "target/classes" ]]; then
        log_info "开始Maven编译..."
        ./mvnw clean compile -DskipTests -q
        log_success "后端编译完成"
    else
        log_info "后端已是最新编译版本，跳过编译"
    fi
    
    cd ..
}

# 安装前端依赖
install_frontend_deps() {
    log_info "检查前端依赖..."
    
    cd vue-vben-admin
    
    # 检查是否需要安装依赖
    if [[ ! -d "node_modules" ]] || [[ "package.json" -nt "node_modules" ]]; then
        log_info "安装前端依赖..."
        pnpm install --silent
        log_success "前端依赖安装完成"
    else
        log_info "前端依赖已是最新版本，跳过安装"
    fi
    
    cd ..
}

# 检测网络变化
detect_network_change() {
    local current_ip=""
    
    # 跨平台IP检测
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        current_ip=$(ifconfig | grep -E "inet [0-9]" | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
    else
        # Linux
        current_ip=$(hostname -I | awk '{print $1}')
    fi
    
    # 如果无法获取IP，使用localhost
    if [[ -z "$current_ip" ]]; then
        current_ip="localhost"
    fi
    
    local last_ip=""
    
    # 读取上次保存的IP
    if [[ -f ".network-config" ]]; then
        last_ip=$(grep "LAST_IP" .network-config 2>/dev/null | cut -d'"' -f2 || echo "")
    fi
    
    log_info "当前IP: $current_ip"
    if [[ -n "$last_ip" ]]; then
        log_info "上次IP: $last_ip"
        
        if [[ "$current_ip" != "$last_ip" ]]; then
            log_warning "检测到网络IP变化: $last_ip -> $current_ip"
            return 0  # 需要更新
        else
            log_success "网络IP未变化，无需更新配置"
            return 1  # 不需要更新
        fi
    else
        log_info "首次运行，需要初始化网络配置"
        return 0  # 需要更新
    fi
}

# 智能网络配置更新
update_network_config() {
    log_info "检查网络配置..."
    
    # 检测网络变化
    if detect_network_change; then
        log_info "更新网络配置..."
        
        # 使用超时机制防止卡住
        if timeout 30 ./update-network-config.sh --auto >/dev/null 2>&1; then
            log_success "网络配置更新完成"
        else
            log_warning "网络配置更新超时，使用默认配置"
            # 使用localhost作为fallback
            export CURRENT_IP="localhost"
        fi
    else
        log_success "网络配置无需更新，使用现有配置"
    fi
}

# 启动服务
start_services() {
    log_info "启动系统服务..."
    
    # 启动后端
    log_info "启动后端服务..."
    cd backend
    nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=external > ../backend.log 2>&1 &
    backend_pid=$!
    cd ..
    
    # 等待后端启动
    log_info "等待后端服务启动..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/api/status >/dev/null 2>&1; then
            log_success "后端服务启动成功 (PID: $backend_pid)"
            break
        elif [ $i -eq 30 ]; then
            log_error "后端服务启动超时"
            tail -20 backend.log
            exit 1
        else
            sleep 2
        fi
    done
    
    # 启动前端
    log_info "启动前端服务..."
    cd vue-vben-admin/apps/web-antd
    nohup pnpm dev > ../../../frontend.log 2>&1 &
    frontend_pid=$!
    cd ../../..
    
    # 等待前端启动
    log_info "等待前端服务启动..."
    frontend_started=false
    for i in {1..30}; do
        # 检查前端是否在指定端口启动
        if curl -s http://localhost:5666 >/dev/null 2>&1; then
            log_success "前端服务启动成功 (PID: $frontend_pid) - 端口: 5666"
            frontend_started=true
            break
        # 检查进程是否还在运行
        elif ! kill -0 "$frontend_pid" 2>/dev/null; then
            log_error "前端服务进程异常退出"
            tail -20 frontend.log
            exit 1
        # 检查是否在其他端口启动（作为fallback）
        elif [ $i -gt 10 ]; then
            # 尝试检测实际端口
            actual_port=$(tail -5 frontend.log | grep -o "localhost:[0-9]*" | cut -d: -f2 | head -1)
            if [[ -n "$actual_port" ]] && curl -s http://localhost:$actual_port >/dev/null 2>&1; then
                log_warning "前端服务在端口 $actual_port 启动（而非预期的5666）"
                frontend_started=true
                break
            fi
        fi
        
        if [ $i -eq 30 ]; then
            log_warning "前端服务启动检查超时，但进程可能仍在启动中"
            log_info "请检查 frontend.log 获取详细信息"
            frontend_started=true  # 继续执行，不阻塞系统启动
            break
        else
            sleep 2
        fi
    done
    
    # 保存进程ID
    echo "$backend_pid" > .backend.pid
    echo "$frontend_pid" > .frontend.pid
}

# 显示系统信息
show_system_info() {
    local current_ip=$(grep "LAST_IP" .network-config 2>/dev/null | cut -d'"' -f2 || echo "localhost")
    
    echo ""
    echo "🎉 无人机管理系统启动完成！"
    echo "========================================"
    echo "📍 系统IP地址: $current_ip"
    echo ""
    echo "🐳 Docker服务:"
    echo "   • PostgreSQL:  http://$current_ip:5432"
    echo "   • InfluxDB:    http://$current_ip:8086"
    echo "   • EMQX MQTT:   tcp://$current_ip:1883"
    echo "   • EMQX 控制台:  http://$current_ip:18083 (admin/public)"
    echo ""
    echo "🚀 应用服务:"
    echo "   • 后端服务:     http://$current_ip:8080"
    echo "   • 前端界面:     http://$current_ip:5666"
    echo "   • API状态:     http://$current_ip:8080/api/status"
    echo "   • API文档:     http://$current_ip:8080/swagger-ui.html"
    echo ""
    echo "📁 日志文件:"
    echo "   • 后端日志: tail -f backend.log"
    echo "   • 前端日志: tail -f frontend.log"
    echo "   • Docker日志: docker-compose logs -f"
    echo ""
    echo "🛑 停止系统: ./stop-system.sh"
    echo "🔄 重启系统: ./restart-system.sh"
    echo "⚙️  更新配置: ./update-network-config.sh"
    echo "🐳 Docker管理: docker-compose [up|down|restart]"
    echo ""
}

# 创建监控脚本
create_monitoring_scripts() {
    # 创建停止脚本
    cat > stop-system.sh << 'EOF'
#!/bin/bash

echo "🛑 停止无人机管理系统..."

# 停止服务
if [[ -f .backend.pid ]]; then
    backend_pid=$(cat .backend.pid)
    if kill -0 "$backend_pid" 2>/dev/null; then
        echo "停止后端服务 (PID: $backend_pid)..."
        kill "$backend_pid"
    fi
    rm -f .backend.pid
fi

if [[ -f .frontend.pid ]]; then
    frontend_pid=$(cat .frontend.pid)
    if kill -0 "$frontend_pid" 2>/dev/null; then
        echo "停止前端服务 (PID: $frontend_pid)..."
        kill "$frontend_pid"
    fi
    rm -f .frontend.pid
fi

# 强制停止相关进程
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "vite.*5666" 2>/dev/null || true

echo "✅ 系统已停止"
EOF

    # 创建重启脚本
    cat > restart-system.sh << 'EOF'
#!/bin/bash

echo "🔄 重启无人机管理系统..."

# 停止系统
./stop-system.sh

# 等待进程完全停止
sleep 3

echo "🚀 重新启动系统..."
# 重新启动系统（包含智能网络检测）
./start-system.sh
EOF

    # 创建状态检查脚本
    cat > check-status.sh << 'EOF'
#!/bin/bash

echo "📊 无人机管理系统状态检查"
echo "========================"

# 检查后端状态
echo -n "后端服务: "
if curl -s http://localhost:8080/api/status >/dev/null 2>&1; then
    echo "✅ 运行中"
    backend_status=$(curl -s http://localhost:8080/api/status | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', 'unknown'))")
    echo "   状态: $backend_status"
else
    echo "❌ 未运行"
fi

# 检查前端状态
echo -n "前端服务: "
if curl -s http://localhost:5666 >/dev/null 2>&1; then
    echo "✅ 运行中"
else
    echo "❌ 未运行"
fi

# 检查进程
echo ""
echo "运行中的相关进程:"
ps aux | grep -E "(spring-boot|vite.*5666)" | grep -v grep || echo "  无相关进程"

# 检查端口占用
echo ""
echo "端口占用情况:"
lsof -i :8080 2>/dev/null | head -2 || echo "  端口 8080: 未占用"
lsof -i :5666 2>/dev/null | head -2 || echo "  端口 5666: 未占用"
EOF

    chmod +x stop-system.sh restart-system.sh check-status.sh
}

# 主函数
main() {
    echo ""
    echo "🚀 无人机管理系统智能启动器"
    echo "============================="
    
    # 系统检查
    check_dependencies
    check_project_files
    check_docker_services
    check_ports
    
    echo ""
    log_header "准备系统环境..."
    
    # 构建项目
    build_backend
    install_frontend_deps
    
    echo ""
    log_header "更新网络配置..."
    
    # 更新网络配置（带超时保护）
    update_network_config
    
    echo ""
    log_header "启动系统服务..."
    
    # 启动服务
    start_services
    
    # 创建监控脚本
    create_monitoring_scripts
    
    # 显示系统信息
    show_system_info
}

# 脚本入口
main "$@" 