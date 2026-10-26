import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/** 后端 geodata-service 地址（可通过环境变量 GEODATA_API 覆盖） */
const API_TARGET = process.env.GEODATA_API || 'http://localhost:8982'
/** whulri 基座 whulri-system 地址（行政区划 /region 等，可通过 BASE_API 覆盖） */
const BASE_API_TARGET = process.env.BASE_API || 'http://localhost:9201'

export default defineConfig(() => {
  return {
    base: '/',
    // Keep separate checkouts from invalidating each other's dependency cache.
    cacheDir: '.vite',
    optimizeDeps: { include: ['element-plus/es'] },
    server: {
      port: 9200,
      open: false,
      proxy: {
        // 前端统一走 /api 前缀，转发到 geodata-service:8982 并把 /api 去掉
        '/api': {
          target: API_TARGET,
          changeOrigin: true,
          secure: false,
          rewrite: (p) => p.replace(/^\/api/, ''),
        },
        // 基座服务（行政区划 /region 等）：/base-api → whulri-system:9201
        '/base-api': {
          target: BASE_API_TARGET,
          changeOrigin: true,
          secure: false,
          rewrite: (p) => p.replace(/^\/base-api/, ''),
        },
      },
    },
    plugins: [
      vue(),
      vueJsx(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'src/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('element-plus')) return 'element-plus'
              if (id.includes('@element-plus/icons-vue')) return 'ep-icons'
              if (id.includes('node_modules/ol')) return 'openlayers'
              if (id.includes('pinia') || id.includes('vue-router')) return 'vue-state'
              return 'vendor'
            }
          },
        },
      },
    },
  }
})
