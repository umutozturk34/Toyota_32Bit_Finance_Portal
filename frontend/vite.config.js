import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('echarts') || id.includes('echarts-for-react') || id.includes('zrender')) return 'vendor-echarts';
          if (id.includes('lightweight-charts')) return 'vendor-lwchart';
          if (id.includes('framer-motion') || id.includes('motion-utils') || id.includes('motion-dom')) return 'vendor-motion';
          if (id.includes('react-icons')) return 'vendor-icons';
          if (id.includes('react-grid-layout') || id.includes('@dnd-kit')) return 'vendor-layout';
          if (id.includes('keycloak-js')) return 'vendor-keycloak';
          if (id.includes('@tanstack/react-query')) return 'vendor-query';
          if (id.includes('i18next')) return 'vendor-i18n';
          return 'vendor';
        },
      },
    },
  },
})
