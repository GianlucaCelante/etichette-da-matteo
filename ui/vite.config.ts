import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// L'interfaccia e' servita dal servizio Spring Boot come file statici (base "/").
// In sviluppo Vite gira sulla sua porta e inoltra /api al servizio su :8765,
// compreso lo stream SSE di /api/eventi: changeOrigin e nessun buffering,
// cosi' gli eventi arrivano al browser mano a mano che il servizio li manda.
//
// La destinazione si puo' cambiare con VITE_API_PROXY_TARGET (per esempio
// per puntare a `npm run mock` su un'altra porta durante lo sviluppo
// dell'interfaccia, senza toccare il valore di serie usato col servizio vero).
const destinazioneApi = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8765';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: destinazioneApi,
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
