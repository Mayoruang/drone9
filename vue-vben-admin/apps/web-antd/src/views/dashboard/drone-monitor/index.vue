<template>
  <DroneMonitor />
</template>

<script lang="ts" setup>
import DroneMonitor from './DroneMonitor.vue';
</script>

<route lang="yaml">
name: DroneMonitor
meta:
  title: 无人机监控
  icon: dashboard-outlined
  order: 1
  requiresAuth: true
</route>
