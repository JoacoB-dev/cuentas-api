# cuentas-web

Frontend Angular de cuentas-api. La documentación completa (pantallas, decisiones, cómo correrlo y tests) está en el [README principal](../README.md) ([English](../README.en.md)).

```bash
npm ci
npm start             # http://localhost:4200 (proxy de /api a http://localhost:8080)
npm test              # tests unitarios (Vitest)
npm run lint
npm run build
npx playwright test   # e2e contra el stack de Docker Compose (http://localhost:4201)
```

Requiere Node.js 24 (o 22.22.3 o superior).
