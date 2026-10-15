FROM redis:7.4

# redis.conf (from redis_example.conf; not committed — it holds the password) logs to
# /var/log/redis and persists to /var/lib/redis, so both must exist and be writable.
RUN mkdir -p /var/lib/redis /var/log/redis /usr/local/etc/redis \
 && chown redis:redis /var/lib/redis /var/log/redis

COPY redis.conf /usr/local/etc/redis/redis.conf

CMD [ "redis-server", "/usr/local/etc/redis/redis.conf" ]
