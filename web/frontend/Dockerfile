# ===== 阶段 1：构建 Vue 前端 =====
FROM node:22-alpine AS build

WORKDIR /app

# 先复制包文件，安装依赖（利用 Docker 层缓存）
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码并构建
COPY index.html vite.config.js ./
COPY public ./public
COPY src ./src
RUN npm run build

# ===== 阶段 2：Nginx 服务静态文件 + API 代理 =====
FROM nginx:alpine

# 清理默认静态文件
RUN rm -rf /usr/share/nginx/html/*

# 复制构建产物
COPY --from=build /app/dist /usr/share/nginx/html

# 复制自定义 nginx 配置（覆盖 default.conf）
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Docker 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
