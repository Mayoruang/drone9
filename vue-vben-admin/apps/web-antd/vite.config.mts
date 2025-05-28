import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  // 智能API目标检测
  const getApiTarget = () => {
    // 从环境变量获取API URL，如果没有则使用localhost
    const envApiUrl = process.env.VITE_API_URL || process.env.VITE_GLOB_API_URL;
    if (envApiUrl) {
      // 如果环境变量中包含完整URL，提取主机部分
      try {
        const url = new URL(envApiUrl);
        return `${url.protocol}//${url.host}`;
      } catch {
        // 如果不是完整URL，直接使用
        return envApiUrl.replace('/api', '');
      }
    }
    // 默认使用localhost
    return 'http://localhost:8080';
  };

  const apiTarget = getApiTarget();
  console.log(`🌐 API代理目标: ${apiTarget}`);

  return {
    application: {},
    vite: {
      server: {
        port: 5666,
        host: '0.0.0.0', // 监听所有接口，允许外部访问
        proxy: {
          '/api': {
            changeOrigin: true,
            target: apiTarget,
            ws: true,
            configure: (proxy, options) => {
              console.log(`📡 配置API代理: /api -> ${apiTarget}`);
            }
          },
          '/ws': {
            changeOrigin: true,
            target: apiTarget, // 保持HTTP协议，让SockJS处理协议升级
            ws: true,
            configure: (proxy, options) => {
              console.log(`🔌 配置WebSocket代理: /ws -> ${apiTarget}`);
            }
          },
        },
      },
    },
  };
});
