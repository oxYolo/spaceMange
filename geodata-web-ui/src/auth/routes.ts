import type { RouteRecordRaw } from 'vue-router'

/** Standalone login page. Business routes do not require authentication. */
export const authRoutes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('./LoginView.vue'), meta: { title: '账户登录' } },
]
