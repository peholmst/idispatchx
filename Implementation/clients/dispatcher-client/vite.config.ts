import { defineConfig } from 'vite'

export default defineConfig({
  root: 'src',
  build: {
    outDir: '../dist',
    emptyOutDir: true
  },
  test: {
    include: ['**/*.test.ts'],
    environment: 'node',
  },
})
