-- ============================================================
-- 用户默认头像：修正 avatar/defaultAvator.png 的拼写错误（缺 r）
-- ============================================================
-- 背景：新用户注册时，UserServiceImpl#defaultAvatarUrl() 会把
--      CosConfig.defaultAvatarKey 拼成 sys_user.avatar 写库：
--        https://<bucket>.cos.<region>.myqcloud.com/avatar/<key>
--      该 key 由环境变量 DEFAULT_AVATAR_KEY 提供，历史上手误写成
--      avatar/defaultAvator.png（少一个 r），而桶里真实存在的对象是
--      avatar/defaultAvatar.png。
--      结果：受影响用户的默认头像 URL 指向 COS 404，
--      前端 profile.vue / my.vue 用 <image :src="userInfo.avatar"> 直接引用
--      （浏览器不会带 Authorization 头，也没有任何 @error 兜底）→ 破图。
--
-- 与代码改动的分工：
--   新注册用户 → 已修 .env / .env.example 的 DEFAULT_AVATAR_KEY + CosConfig 默认值
--   存量数据   → 需要本脚本回填（改配置不会自动纠正历史行）
--
-- 幂等：只命中「拼错的那个 key」，用户自己上传的头像是 avatar/<32位hex>.png
--      形态，LIKE '%defaultAvator%' 匹配不到，重复执行不产生副作用。
--      已修正过的行不再匹配，第二次执行为 0 行受影响。
--
-- 执行前可先自查影响范围：
--   SELECT id, account, avatar FROM sys_user WHERE avatar LIKE '%defaultAvator%';
-- ============================================================

-- ---------- 1. 回填存量数据 ----------
-- 只替换文件名部分，保留各自原有的 bucket / region（不同环境可能指向不同桶）。
UPDATE `sys_user`
SET `avatar` = REPLACE(`avatar`, '/avatar/defaultAvator.png', '/avatar/defaultAvatar.png')
WHERE `avatar` LIKE '%/avatar/defaultAvator.png';

-- ---------- 2. 执行结果自查（应为 0）----------
SELECT id, account, avatar
FROM `sys_user`
WHERE avatar LIKE '%defaultAvator%';

-- ---------- 3. 兜底：库若存在 dr=0 的逻辑删除行，逻辑删除用户同样不应保留 404 地址 ----------
-- 说明：上面那条 UPDATE 不带 dr 条件，会连已逻辑删除的行一起修正；
--       若不希望动历史行，可自行加 `AND dr = 0`。
-- 这里保留无 dr 条件的写法：逻辑删除行将来恢复时同样需要可用的头像地址。