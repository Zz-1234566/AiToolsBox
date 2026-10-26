-- =====================================================================
-- AI 模型系统级种子：DeepSeek（BYOK 测试2）
-- 适用版本：v1 (2026-10-12)
-- 说明：
--   1) 新增 2 条 DeepSeek 模型行（chat / reasoner），capability=text
--   2) api_key / api_key_encrypted / api_key_prefix 全部 NULL
--      —— 用户从 my-models 页 UI 自行配置 (BYOK)；本脚本不写明文 Key
--   3) is_default=0（minimax-text 仍为系统默认；用户可在「我的 AI 模型」页把 deepseek 设为自己的默认）
--   4) on duplicate key update: 已存在的 model_code 仅刷新元数据，
--      绝不触碰任何 Key 字段（保护用户已配的 Key）
-- =====================================================================

INSERT INTO sys_ai_model
    (model_code, model_name, provider, capability, api_url, api_model,
     api_key, api_key_encrypted, api_key_prefix, is_default, status, sort_no)
VALUES
    ('deepseek-chat',     'DeepSeek Chat',     'deepseek', 'text',
     'https://api.deepseek.com/v1/chat/completions', 'deepseek-chat',
     NULL, NULL, NULL, 0, 1, 60),

    ('deepseek-reasoner', 'DeepSeek Reasoner', 'deepseek', 'text',
     'https://api.deepseek.com/v1/chat/completions', 'deepseek-reasoner',
     NULL, NULL, NULL, 0, 1, 61)
ON DUPLICATE KEY UPDATE
    model_name = VALUES(model_name),
    provider   = VALUES(provider),
    capability = VALUES(capability),
    api_url    = VALUES(api_url),
    api_model  = VALUES(api_model),
    is_default = VALUES(is_default),
    status     = VALUES(status),
    sort_no    = VALUES(sort_no);
-- 注意：on duplicate key update 故意不更新 api_key / api_key_encrypted / api_key_prefix
-- 防止覆盖任何已配置/将来通过 UI 写入的 Key
