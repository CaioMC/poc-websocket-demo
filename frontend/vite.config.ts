import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * O build é gerado diretamente em `target/classes/static`, de onde o Spring Boot
 * serve os arquivos estáticos — assim `mvn spring-boot:run` já sobe a aplicação
 * com a interface pronta, sem passo manual.
 *
 * Em desenvolvimento (`npm run dev`), o servidor do Vite faz proxy do WebSocket
 * para o backend em :3000, permitindo hot reload da UI com o backend real.
 */
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/ws': {
        target: 'ws://localhost:3000',
        ws: true,
      },
    },
  },
});
