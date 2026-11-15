-- Read for RedisVersionedCache (common-resilience): the entry and its remaining lifetime in one atomic call,
-- so freshness is decided by Redis's clock, never by pod clocks.
--
-- KEYS[1] = entry key, e.g. rooms::v2:42
--
-- Returns {} if absent, else { v, d, pttl }:
--   v    = data version
--   d    = JSON value ("" = "not found" / deleted)
--   pttl = remaining milliseconds, "-1" = no expiry
-- The entry is stale once pttl <= the key space's stale window.

local fields = redis.call('HMGET', KEYS[1], 'v', 'd')
if not fields[1] then
  return {}
end

return { fields[1], fields[2] or '', tostring(redis.call('PTTL', KEYS[1])) }
