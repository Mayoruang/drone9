import { defineOverridesPreferences } from '@vben/preferences';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    name: 'Drone9 无人机管理系统',
    defaultHomePath: '/drone-registration',
  },
  logo: {
    enable: true,
    source: '/drone-icon.svg', // 使用无人机图标
  },
  copyright: {
    companyName: 'Drone9 Team',
    companySiteLink: 'https://github.com/drone9',
    date: '2024',
    enable: true,
    icp: '',
    icpLink: '',
    settingShow: true,
  },
});
