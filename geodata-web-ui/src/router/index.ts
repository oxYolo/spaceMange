import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { authRoutes } from '@/auth/routes'
import { businessRoutes } from '@/business/routes'

export const routes: RouteRecordRaw[] = [
  ...authRoutes,
  ...businessRoutes,
  { path: '/:pathMatch(.*)*', redirect: '/manage' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() { return { top: 0 } },
})

export default router
