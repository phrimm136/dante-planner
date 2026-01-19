FROM nginx:alpine

RUN apk add --no-cache wget

COPY nginx.conf /etc/nginx/nginx.conf
COPY locations.conf /etc/nginx/locations.conf

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
