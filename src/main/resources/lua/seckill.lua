-- 优惠券 id
local voucherId = ARGV[1]
-- 用户 id
local userId = ARGV[2]
-- 订单 id
local orderId = ARGV[3]

-- 库存 key
local stockKey = "seckill:stock:" .. voucherId
-- 订单 key
local orderKey = "seckill:order:" .. voucherId

-- 判断库存是否充足
if (tonumber(redis.call("get", stockKey)) <= 0) then
    return 1
end

-- 判断用户是否已经下单
if (redis.call("sismember", orderKey, userId) == 1) then
    return 2
end

-- 保存用户 id，扣库存
redis.call("incrby", stockKey, -1)
redis.call("sadd", orderKey, userId)

-- 向队列中发送消息
redis.call("xadd", "stream.orders", "*", "userId", userId, "id", orderId, "voucherId", voucherId)

-- 成功返回 0
return 0