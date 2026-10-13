# Etapa 1: exportar la aplicación Expo para web
FROM node:24-bookworm-slim AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# URL pública de la API, integrada al compilar
ARG EXPO_PUBLIC_API_URL

RUN test -n "$EXPO_PUBLIC_API_URL" || \
    (echo "Falta EXPO_PUBLIC_API_URL" && exit 1)

ENV EXPO_PUBLIC_API_URL=${EXPO_PUBLIC_API_URL}

RUN npx expo export --platform web


# Etapa 2: servir los recursos web
FROM nginx:stable-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 7777

CMD ["nginx", "-g", "daemon off;"]
