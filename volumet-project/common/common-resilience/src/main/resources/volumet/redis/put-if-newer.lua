-- Version-ordered write for RedisVersionedCache (common-resilience).
-- Redis runs the whole script atomically: no other command, from any instance, runs between
-- reading the stored version and writing the new one.
--
-- Entry layout: a hash with fields
--   v = data version (RedisVersionedCache.VERSION_FIELD)
--   d = JSON value, "" for "not found" / deleted (RedisVersionedCache.DATA_FIELD)
--
-- KEYS[1] = entry key, e.g. rooms::v2:42
-- ARGV[1] = new data version
-- ARGV[2] = JSON value ("" = null)
-- ARGV[3] = storage TTL in milliseconds, ttl + stale window (0 = no expiry)
--
-- Returns 1 if written, 0 if not:
--   stored version newer  -> nothing changes (the write is dropped)
--   stored version equal  -> data kept, TTL renewed (the version was just confirmed current: fresh again)

local key = KEYS[1]
local new_version = tonumber(ARGV[1])
local value = ARGV[2]
local ttl_millis = tonumber(ARGV[3])

local current_version = redis.call('HGET', key, 'v')
if current_version then
  local current = tonumber(current_version)
  if current > new_version then
    return 0
  end
  if current == new_version then
    if ttl_millis > 0 then
      redis.call('PEXPIRE', key, ttl_millis)
    end
    return 0
  end
end

redis.call('HSET', key, 'v', ARGV[1], 'd', value)

if ttl_millis > 0 then
  redis.call('PEXPIRE', key, ttl_millis)
else
  redis.call('PERSIST', key)
end

return 1
