/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Đặc tả API mục 11: giao diện và API CÙNG origin -> không mở CORS.
// Khi chạy dev, Vite chuyển /api sang backend Spring Boot; khi triển khai, Nginx làm việc này.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
  },
});
