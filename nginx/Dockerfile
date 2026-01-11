FROM nginx:alpine

RUN apk add --no-cache wget

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
