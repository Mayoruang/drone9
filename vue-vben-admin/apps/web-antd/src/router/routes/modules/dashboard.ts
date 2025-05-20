import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: $t('page.dashboard.title'),
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name: 'Analytics',
        path: '/analytics',
        component: () => import('#/views/dashboard/analytics/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: $t('page.dashboard.analytics'),
        },
      },
      {
        name: 'Workspace',
        path: '/workspace',
        component: () => import('#/views/dashboard/workspace/index.vue'),
        meta: {
          icon: 'carbon:workspace',
          title: $t('page.dashboard.workspace'),
        },
      },
      {
        name: 'DroneMonitor',
        path: '/drone-monitor',
        component: () => import('#/views/dashboard/drone-monitor/index.vue'),
        meta: {
          icon: 'mdi:drone',
          title: '无人机监控中心',
          order: 1
        },
      },
      {
        name: 'DroneRegistration',
        path: '/drone-registration',
        component: () => import('#/views/dashboard/drone-registration/index.vue'),
        meta: {
          icon: 'mdi:clipboard-text-outline',
          title: '无人机注册管理',
          order: 2
        },
      },
      {
        name: 'DroneStatus',
        path: '/drone-status',
        component: () => import('#/views/dashboard/drone-status/index.vue'),
        meta: {
          icon: 'lucide:activity-square',
          title: '无人机状态监控',
          order: 3
        },
      },
    ],
  },
];

export default routes;
