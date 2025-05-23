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
    redirect: '/drone-registration',
    children: [
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
      {
        name: 'Geofence',
        path: '/geofence',
        component: () => import('#/views/dashboard/geofence/index.vue'),
        meta: {
          icon: 'mdi:map-marker-radius',
          title: '地理围栏管理',
          order: 4
        },
      },
    ],
  },
];

export default routes;
