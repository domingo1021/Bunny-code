FROM nginx:1.23.1

COPY certificate_final.crt /etc/ssl/
COPY private.key /etc/ssl/
COPY stylish-server.conf /etc/nginx/conf.d
RUN rm /etc/nginx/conf.d/default.conf