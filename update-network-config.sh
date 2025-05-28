#!/bin/bash

# 智能网络配置更新脚本
# 自动检测IP变化并更新前端、后端配置

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置常量
BACKEND_PORT=8080
FRONTEND_PORT=5666
CONFIG_FILE=".network-config"

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

# 获取当前活跃的网络IP地址
get_current_ip() {
    local ip=""
    
    # 尝试多种方法获取IP
    # 方法1: 通过route命令获取默认网关对应的接口IP
    if command -v route >/dev/null 2>&1; then
        ip=$(route get default 2>/dev/null | grep interface | awk '{print $2}' | xargs ifconfig 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
    fi
    
    # 方法2: 通过网络服务检测 (备用方案)
    if [[ -z "$ip" ]]; then
        ip=$(curl -s --max-time 3 http://checkip.amazonaws.com/ 2>/dev/null || echo "")
    fi
    
    # 方法3: 通过系统网络接口检测
    if [[ -z "$ip" ]]; then
        ip=$(ifconfig | grep -E "inet.*broadcast" | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
    fi
    
    # 方法4: 通过netstat检测活跃连接的本地IP
    if [[ -z "$ip" ]]; then
        ip=$(netstat -rn | grep default | head -1 | awk '{print $NF}' | xargs ifconfig 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
    fi
    
    echo "$ip"
}

# 读取上次保存的IP配置
read_last_config() {
    if [[ -f "$CONFIG_FILE" ]]; then
        source "$CONFIG_FILE"
        echo "$LAST_IP"
    else
        echo ""
    fi
}

# 保存当前IP配置
save_current_config() {
    local current_ip="$1"
    cat > "$CONFIG_FILE" << EOF
# 网络配置缓存文件
LAST_IP="$current_ip"
LAST_UPDATE="$(date)"
EOF
}

# 更新后端CORS配置
update_backend_cors() {
    local current_ip="$1"
    local config_file="backend/src/main/resources/application-external.yml"
    
    if [[ ! -f "$config_file" ]]; then
        log_error "后端配置文件不存在: $config_file"
        return 1
    fi
    
    log_info "更新后端CORS配置..."
    
    # 构建新的allowed-origins字符串
    local base_origins="http://localhost:3000,http://localhost:3100,http://localhost:5666,http://localhost:5667,http://127.0.0.1:5666"
    local new_origins="${base_origins},http://${current_ip}:${FRONTEND_PORT},http://${current_ip}:${BACKEND_PORT}"
    
    # 使用sed更新配置文件
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s|allowed-origins:.*|allowed-origins: \"${new_origins}\"|" "$config_file"
        sed -i '' "s|base-url:.*|base-url: \${APPLICATION_BASE_URL:http://${current_ip}:${BACKEND_PORT}}|" "$config_file"
    else
        # Linux
        sed -i "s|allowed-origins:.*|allowed-origins: \"${new_origins}\"|" "$config_file"
        sed -i "s|base-url:.*|base-url: \${APPLICATION_BASE_URL:http://${current_ip}:${BACKEND_PORT}}|" "$config_file"
    fi
    
    log_success "后端配置已更新"
}

# 更新前端环境配置
update_frontend_config() {
    local current_ip="$1"
    
    # 更新主 .env 文件
    local main_env_file="vue-vben-admin/apps/web-antd/.env"
    if [[ -f "$main_env_file" ]]; then
        # 备份原文件
        cp "$main_env_file" "${main_env_file}.backup"
        
        # 更新API URL配置
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            sed -i '' "s|VITE_GLOB_API_URL=.*|VITE_GLOB_API_URL=http://${current_ip}:${BACKEND_PORT}/api|" "$main_env_file"
            sed -i '' "s|VITE_API_URL=.*|VITE_API_URL=http://${current_ip}:${BACKEND_PORT}/api|" "$main_env_file"
            # 添加或更新WebSocket配置
            if grep -q "VITE_GLOB_WS_URL" "$main_env_file"; then
                sed -i '' "s|VITE_GLOB_WS_URL=.*|VITE_GLOB_WS_URL=ws://${current_ip}:${BACKEND_PORT}|" "$main_env_file"
            else
                echo "VITE_GLOB_WS_URL=ws://${current_ip}:${BACKEND_PORT}" >> "$main_env_file"
            fi
            if grep -q "VITE_WS_URL" "$main_env_file"; then
                sed -i '' "s|VITE_WS_URL=.*|VITE_WS_URL=ws://${current_ip}:${BACKEND_PORT}|" "$main_env_file"
            else
                echo "VITE_WS_URL=ws://${current_ip}:${BACKEND_PORT}" >> "$main_env_file"
            fi
        else
            # Linux
            sed -i "s|VITE_GLOB_API_URL=.*|VITE_GLOB_API_URL=http://${current_ip}:${BACKEND_PORT}/api|" "$main_env_file"
            sed -i "s|VITE_API_URL=.*|VITE_API_URL=http://${current_ip}:${BACKEND_PORT}/api|" "$main_env_file"
            # 添加或更新WebSocket配置
            if grep -q "VITE_GLOB_WS_URL" "$main_env_file"; then
                sed -i "s|VITE_GLOB_WS_URL=.*|VITE_GLOB_WS_URL=ws://${current_ip}:${BACKEND_PORT}|" "$main_env_file"
            else
                echo "VITE_GLOB_WS_URL=ws://${current_ip}:${BACKEND_PORT}" >> "$main_env_file"
            fi
            if grep -q "VITE_WS_URL" "$main_env_file"; then
                sed -i "s|VITE_WS_URL=.*|VITE_WS_URL=ws://${current_ip}:${BACKEND_PORT}|" "$main_env_file"
            else
                echo "VITE_WS_URL=ws://${current_ip}:${BACKEND_PORT}" >> "$main_env_file"
            fi
        fi
    fi
    
    # 更新生产环境配置
    local env_file="vue-vben-admin/apps/web-antd/.env.production"
    cat > "$env_file" << EOF
# 生产环境配置 - 自动生成
VITE_APP_TITLE=无人机管理系统

# API配置 - 自动检测的IP地址
VITE_API_URL=http://${current_ip}:${BACKEND_PORT}
VITE_API_URL_PREFIX=/api
VITE_GLOB_API_URL=http://${current_ip}:${BACKEND_PORT}/api

# WebSocket配置
VITE_WS_URL=ws://${current_ip}:${BACKEND_PORT}
VITE_GLOB_WS_URL=ws://${current_ip}:${BACKEND_PORT}

# 其他配置
VITE_DROP_CONSOLE=true
VITE_DROP_DEBUGGER=true
EOF

    # 同时更新开发环境配置以保持一致
    local dev_env_file="vue-vben-admin/apps/web-antd/.env.development"
    cat > "$dev_env_file" << EOF
# 开发环境配置 - 自动生成
VITE_APP_TITLE=无人机管理系统 (开发)

# API配置 - 自动检测的IP地址
VITE_API_URL=http://${current_ip}:${BACKEND_PORT}
VITE_API_URL_PREFIX=/api
VITE_GLOB_API_URL=http://${current_ip}:${BACKEND_PORT}/api

# WebSocket配置
VITE_WS_URL=ws://${current_ip}:${BACKEND_PORT}
VITE_GLOB_WS_URL=ws://${current_ip}:${BACKEND_PORT}

# 开发环境特殊配置
VITE_DROP_CONSOLE=false
VITE_DROP_DEBUGGER=false
EOF
    
    log_success "前端配置已更新"
}

# 检查并重启服务
restart_services() {
    local restart_backend="$1"
    local restart_frontend="$2"
    
    if [[ "$restart_backend" == "true" ]]; then
        log_info "重启后端服务..."
        
        # 停止现有的后端服务
        pkill -f "spring-boot:run" 2>/dev/null || true
        sleep 2
        
        # 启动后端服务
        cd backend
        nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=external > ../backend.log 2>&1 &
        cd ..
        
        log_success "后端服务重启完成"
    fi
    
    if [[ "$restart_frontend" == "true" ]]; then
        log_info "重启前端服务..."
        
        # 检查前端是否在运行
        if pgrep -f "vite.*5666" > /dev/null; then
            log_info "停止现有前端服务..."
            pkill -f "vite.*5666" 2>/dev/null || true
            sleep 2
        fi
        
        # 启动前端服务
        cd vue-vben-admin/apps/web-antd
        nohup pnpm dev > ../../../frontend.log 2>&1 &
        cd ../../..
        
        log_success "前端服务重启完成"
    fi
}

# 主函数
main() {
    echo ""
    echo "🌐 智能网络配置更新系统"
    echo "================================"
    
    # 获取当前IP
    log_info "检测当前网络IP地址..."
    current_ip=$(get_current_ip)
    
    if [[ -z "$current_ip" ]]; then
        log_error "无法检测到有效的IP地址"
        exit 1
    fi
    
    log_success "检测到IP地址: $current_ip"
    
    # 读取上次配置
    last_ip=$(read_last_config)
    
    # 检查是否需要更新
    if [[ "$current_ip" == "$last_ip" ]]; then
        log_info "IP地址未变化，无需更新配置"
        
        # 检查参数是否强制更新
        if [[ "$1" != "--force" ]] && [[ "$1" != "-f" ]]; then
            echo ""
            echo "💡 提示: 使用 --force 或 -f 参数可强制更新配置"
            exit 0
        fi
        
        log_warning "强制更新模式已启用"
    fi
    
    echo ""
    log_info "开始更新网络配置..."
    
    # 更新配置文件
    update_backend_cors "$current_ip"
    update_frontend_config "$current_ip"
    
    # 保存配置
    save_current_config "$current_ip"
    
    # 询问是否重启服务
    restart_backend="false"
    restart_frontend="false"
    
    if [[ "$1" == "--auto" ]] || [[ "$1" == "-a" ]]; then
        restart_backend="true"
        restart_frontend="true"
        log_info "自动模式：将重启所有服务"
    else
        echo ""
        read -p "是否重启后端服务? (y/N): " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            restart_backend="true"
        fi
        
        read -p "是否重启前端服务? (y/N): " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            restart_frontend="true"
        fi
    fi
    
    # 重启服务
    if [[ "$restart_backend" == "true" ]] || [[ "$restart_frontend" == "true" ]]; then
        restart_services "$restart_backend" "$restart_frontend"
        
        echo ""
        log_info "等待服务启动..."
        sleep 10
        
        # 测试服务
        if curl -s "http://localhost:${BACKEND_PORT}/api/status" > /dev/null; then
            log_success "后端服务正常运行"
        else
            log_warning "后端服务可能还在启动中..."
        fi
        
        if curl -s "http://localhost:${FRONTEND_PORT}" > /dev/null; then
            log_success "前端服务正常运行"
        else
            log_warning "前端服务可能还在启动中..."
        fi
    fi
    
    # 显示访问信息
    echo ""
    echo "🎉 配置更新完成！"
    echo "================================"
    echo "📍 当前IP地址: $current_ip"
    echo "🔗 后端地址: http://$current_ip:$BACKEND_PORT"
    echo "🖥️  前端地址: http://$current_ip:$FRONTEND_PORT"
    echo "📊 状态检查: http://$current_ip:$BACKEND_PORT/api/status"
    echo ""
    echo "💡 使用说明:"
    echo "   • --auto/-a   : 自动模式，无需确认直接重启服务"
    echo "   • --force/-f  : 强制更新配置（即使IP未变化）"
    echo ""
}

# 脚本入口
main "$@" 