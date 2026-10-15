FROM nginx:1.28-alpine

# sites-enabled/bunny.conf is created on the server from bunny_example.conf (it names the
# domain and certificate paths, so it is not committed).
RUN rm /etc/nginx/conf.d/default.conf
COPY ./sites-enabled/bunny.conf /etc/nginx/conf.d/
