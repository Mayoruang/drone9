/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { useAuthStore } from '#/store';

import { refreshTokenApi } from './core';

// 扩展Window接口以支持错误时间记录
declare global {
  interface Window {
    _lastErrorTimes?: Record<string, number>;
  }
}

// Get apiURL from env, if not provided, default to '/api'
const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const DEFAULT_API_URL = '/api';
const finalApiURL = apiURL ?? DEFAULT_API_URL;

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });

  /**
   * 重新认证逻辑
   */
  async function doReAuthenticate() {
    console.warn('Access token or refresh token is invalid or expired. ');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    accessStore.setAccessToken(null);
    if (
      preferences.app.loginExpiredMode === 'modal' &&
      accessStore.isAccessChecked
    ) {
      accessStore.setLoginExpired(true);
    } else {
      await authStore.logout();
    }
  }

  /**
   * 刷新token逻辑
   */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const resp = await refreshTokenApi();
    const newToken = resp.data;
    accessStore.setAccessToken(newToken);
    return newToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;
      return config;
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 这里可以根据业务进行定制,你可以拿到 error 内的信息进行定制化处理，根据不同的 code 做不同的提示，而不是直接使用 message.error 提示 msg
      // 当前mock接口返回的错误字段是 error 或者 message
      const responseData = error?.response?.data ?? {};
      const errorMessage = responseData?.error ?? responseData?.message ?? '';

      // 过滤掉一些不需要显示的错误
      const url = error?.config?.url || '';
      const status = error?.response?.status;
      const method = error?.config?.method?.toUpperCase();

      console.log(`🔍 全局错误拦截器检查: ${method} ${url}, status=${status}, success=${responseData?.success}`);

      // 🚫 完全禁用无人机相关API的全局错误提示，让前端组件自己处理
      if (url.includes('/drones/') || url.includes('/commands')) {
        console.log('🚫 跳过所有无人机API的全局错误提示，交由前端组件处理');
        return;
      }

      // 防止重复错误提示（同样的错误信息在3秒内不重复显示）
      const errorKey = `${method}_${url}_${status}_${errorMessage || msg}`;
      const now = Date.now();
      const lastErrorTime = window._lastErrorTimes?.[errorKey] || 0;

      if (now - lastErrorTime < 3000) {
        console.log('⏭️ 跳过重复错误提示:', errorKey);
        return;
      }

      // 记录错误时间
      window._lastErrorTimes = window._lastErrorTimes || {};
      window._lastErrorTimes[errorKey] = now;

      // 显示错误信息
      console.error('🚨 API错误:', { url, status, method, msg, errorMessage, responseData, error });
      message.error(errorMessage || msg);
    }),
  );

  return client;
}

export const requestClient = createRequestClient(finalApiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: finalApiURL });
