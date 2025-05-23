import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            // Remove the rewrite to keep the full path
            // rewrite: (path) => path.replace(/^\/api/, ''),
            // Proxy to backend server
            target: 'http://localhost:8080',
            ws: true,
          },
          '/ws': {
            changeOrigin: true,
            target: 'ws://localhost:8080',
            ws: true,
          },
        },
      },
    },
  };
});
