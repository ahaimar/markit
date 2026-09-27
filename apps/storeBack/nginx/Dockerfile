FROM nginx:1.27-alpine

RUN apk add --no-cache openssl

COPY nginx.conf /etc/nginx/nginx.conf

RUN mkdir -p /etc/nginx/certs /var/www/certbot /var/log/nginx

EXPOSE 80 443

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost/health || exit 1

STOPSIGNAL SIGTERM

CMD ["nginx", "-g", "daemon off;"]
