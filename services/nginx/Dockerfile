# Service: nginx — reverse proxy, single entry point
FROM nginx:1.27-alpine
LABEL service="nginx"
COPY services/nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
