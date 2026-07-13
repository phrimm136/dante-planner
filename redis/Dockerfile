FROM redis:7-alpine

# Two role configs are baked in; each compose service selects one via `command`:
#   redis-auth.conf      - durable auth store (AOF everysec + RDB preamble, noeviction)
#   redis-ephemeral.conf - per-region rate-limit store (no persistence, memory-bounded)
COPY redis-auth.conf /etc/redis/redis-auth.conf
COPY redis-ephemeral.conf /etc/redis/redis-ephemeral.conf

# Default to the durable config; the ephemeral service overrides `command` in compose.
CMD ["redis-server", "/etc/redis/redis-auth.conf"]
