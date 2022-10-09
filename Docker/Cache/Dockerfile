FROM redis:7.0

RUN mkdir -p /var/lib/redis/ \
&& mkdir -p /usr/local/etc/redis/ \
&& chmod 777 /var/lib/redis


COPY redis.conf /usr/local/etc/redis/redis.conf

CMD [ "redis-server", "/usr/local/etc/redis/redis.conf" ]
