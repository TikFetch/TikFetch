import {defineConfig} from 'vite';
import tailwindcss from '@tailwindcss/vite';
import {resolve} from 'node:path';

export default defineConfig({
    plugins: [tailwindcss()],
    build: {
        manifest: true,
        outDir: resolve(__dirname, 'src/main/resources/static/dist'),
        emptyOutDir: true,
        rollupOptions: {
            input: resolve(__dirname, 'frontend/app.js'),
            output: {
                entryFileNames: 'assets/app.js',
                chunkFileNames: 'assets/[name].js',
                assetFileNames: 'assets/[name][extname]'
            }
        }
    }
});
