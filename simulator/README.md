# 无人机模拟器

这是一个无人机模拟器系统，包含两个主要组件：

## 文件说明

- `drone_simulator.py` - 用于注册新无人机到系统
- `existing_drone_simulator.py` - 用于模拟已注册的无人机
- `requirements.txt` - Python依赖包列表

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用方法

### 1. 注册新无人机

```bash
# 注册一个新无人机（自动生成序列号）
python drone_simulator.py

# 使用自定义序列号注册
python drone_simulator.py --serial "MY-DRONE-001"

# 查看已注册的无人机
python drone_simulator.py --list
```

### 2. 模拟已注册的无人机

```bash
# 查看可用的无人机
python existing_drone_simulator.py --list

# 启动指定无人机的模拟器
python existing_drone_simulator.py --drone-id <无人机ID>
```

## 支持的命令

模拟器支持以下无人机命令：
- `ARM` - 解锁无人机
- `DISARM` - 锁定无人机  
- `TAKEOFF` - 起飞到指定高度
- `LAND` - 降落
- `GOTO` - 前往指定位置
- `HOVER` - 悬停
- `RTL` - 返回起飞点

## 工作流程

1. 使用 `drone_simulator.py` 注册新无人机
2. 等待管理员审批注册请求
3. 使用 `existing_drone_simulator.py` 启动无人机模拟器
4. 通过前端控制面板发送命令控制无人机

## 注意事项

- 确保后端服务运行在 `http://localhost:8080`
- 确保MQTT代理运行在 `localhost:1883`
- 模拟器会在沈阳市区内随机生成初始位置 