#!/bin/bash

# 无人机模拟器演示启动脚本
# 支持多种轨迹模式演示 - 使用完整注册流程

echo "🛸 无人机模拟器轨迹演示启动脚本"
echo "=================================="

# 检查Python是否安装
if ! command -v python &> /dev/null; then
    echo "❌ 错误: 未找到Python，请先激活正确的conda环境"
    exit 1
fi

# 检查依赖
echo "📦 检查依赖..."
python -c "import paho.mqtt.client, requests" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "⚠️  缺少依赖，正在安装..."
    pip install paho-mqtt requests
fi

# 检查后端服务
echo "🔍 检查后端服务..."
if ! curl -s http://localhost:8080/api/v1/health &> /dev/null; then
    echo "⚠️  警告: 无法连接到后端服务 (http://localhost:8080)"
    echo "    请确保后端服务正在运行，否则注册将失败"
    read -p "是否继续？(y/N): " confirm
    if [[ ! $confirm =~ ^[Yy]$ ]]; then
        echo "退出演示脚本"
        exit 1
    fi
fi

# 显示选项菜单
echo ""
echo "请选择演示模式 (所有模式都将执行完整注册流程):"
echo "1. 矩形轨迹巡航演示"
echo "2. 圆形轨迹巡航演示" 
echo "3. 三角形轨迹巡航演示"
echo "4. 直线往复巡航演示"
echo "5. 基础模式(等待MQTT命令)"
echo "6. 批量启动多架无人机"
echo "0. 退出"

read -p "请输入选择 (0-6): " choice

case $choice in
    1)
        echo "🔄 启动矩形轨迹巡航演示..."
        echo "轨迹: 200x150米矩形"
        echo "⏳ 注册流程: 需要等待管理员审批"
        python drone_simulator.py --auto-patrol \
            --trajectory-type RECTANGLE --trajectory-size 200 \
            --telemetry-interval 3 --verbose
        ;;
    2)
        echo "🔵 启动圆形轨迹巡航演示..."
        echo "轨迹: 半径100米圆形，12个点"
        echo "⏳ 注册流程: 需要等待管理员审批"
        python drone_simulator.py --auto-patrol \
            --trajectory-type CIRCLE --trajectory-size 200 \
            --telemetry-interval 3 --verbose
        ;;
    3)
        echo "🔺 启动三角形轨迹巡航演示..."
        echo "轨迹: 边长150米等边三角形"
        echo "⏳ 注册流程: 需要等待管理员审批"
        python drone_simulator.py --auto-patrol \
            --trajectory-type TRIANGLE --trajectory-size 150 \
            --telemetry-interval 3 --verbose
        ;;
    4)
        echo "↔️  启动直线往复巡航演示..."
        echo "轨迹: 300米直线往复"
        echo "⏳ 注册流程: 需要等待管理员审批"
        python drone_simulator.py --auto-patrol \
            --trajectory-type LINE --trajectory-size 300 \
            --telemetry-interval 4 --verbose
        ;;
    5)
        echo "🧪 启动基础模式..."
        echo "模式: 等待MQTT命令"
        echo "⏳ 注册流程: 需要等待管理员审批"
        python drone_simulator.py \
            --telemetry-interval 5 --verbose
        ;;
    6)
        echo "🚁 批量启动多架无人机..."
        echo "启动3架无人机进行轨迹演示"
        echo "⏳ 注册流程: 每架都需要管理员审批"
        
        # 启动第一架 - 矩形轨迹
        echo "启动无人机 SIM-001 (矩形轨迹)..."
        python drone_simulator.py --auto-patrol \
            --serial "SIM-001" --trajectory-type RECTANGLE \
            --trajectory-size 150 --telemetry-interval 4 --verbose &
        
        sleep 2
        
        # 启动第二架 - 圆形轨迹  
        echo "启动无人机 SIM-002 (圆形轨迹)..."
        python drone_simulator.py --auto-patrol \
            --serial "SIM-002" --trajectory-type CIRCLE \
            --trajectory-size 120 --telemetry-interval 5 --verbose &
        
        sleep 2
        
        # 启动第三架 - 三角形轨迹
        echo "启动无人机 SIM-003 (三角形轨迹)..."
        python drone_simulator.py --auto-patrol \
            --serial "SIM-003" --trajectory-type TRIANGLE \
            --trajectory-size 100 --telemetry-interval 6 --verbose &
        
        echo "✅ 3架无人机已启动，每架都需要管理员审批注册请求"
        echo "📋 请登录管理后台批准注册请求"
        echo "🛑 按Ctrl+C停止所有模拟器"
        
        # 等待用户中断
        trap 'echo "🛑 正在停止所有无人机模拟器..."; kill $(jobs -p); exit' INT
        wait
        ;;
    0)
        echo "👋 退出演示脚本"
        exit 0
        ;;
    *)
        echo "❌ 无效选择，请重新运行脚本"
        exit 1
        ;;
esac

echo ""
echo "演示完成！" 