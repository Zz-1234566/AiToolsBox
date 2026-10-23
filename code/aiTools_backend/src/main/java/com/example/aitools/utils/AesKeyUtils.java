package com.example.aitools.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（用于 AI 模型 API Key 落库）。
 *
 * <h3>密钥</h3>
 * <ul>
 *   <li>来源：环境变量 {@code SYS_AI_MODEL_KEY}，编码 32 字节密钥（Base64）。</li>
 *   <li>启动校验：密钥缺失或长度不足 32 字节 → 抛 {@link IllegalStateException}，fail-fast。</li>
 * </ul>
 *
 * <h3>密文布局</h3>
 * 拼接 {@code ciphertext || iv(12) || tag(16)}，存 {@code VARBINARY(512)}。
 * IV 每次随机生成（用 {@link SecureRandom}）；tag 由 GCM 自动追加。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   byte[] encrypted = AesKeyUtils.encrypt("sk-xxxxx");
 *   String plain     = AesKeyUtils.decrypt(encrypted);   // "sk-xxxxx"
 *   String prefix    = AesKeyUtils.maskPrefix("sk-xxxxx"); // "sk-x"
 * </pre>
 *
 * <b>线程安全</b>：{@link SecretKey} 在初始化后只读，{@link Cipher} 每次调用都重新 new（不可重用含状态的 GCM Cipher）。
 */
@Slf4j
@Component
public final class AesKeyUtils {

    /** 环境变量 / 配置项字段名：32 字节 AES 密钥的 Base64 编码 */
    public static final String KEY_PROPERTY = "SYS_AI_MODEL_KEY";

    /** AES-256 = 32 字节 */
    private static final int KEY_LENGTH_BYTES = 32;

    /** GCM IV 长度（推荐 12 字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** GCM Tag 长度（位），对应 16 字节 */
    private static final int GCM_TAG_BITS = 128;

    /** 算法名 */
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    /** 从配置文件注入 Base64 编码的 32 字节密钥（生产环境必须通过环境变量 SYS_AI_MODEL_KEY 注入） */
    @Value("${aitools.crypto.sys-ai-model-key:${SYS_AI_MODEL_KEY:}}")
    private String base64Key;

    /** 解码后的 32 字节密钥 */
    private static volatile SecretKey secretKey;

    /** 静态实例引用，供 @PostConstruct 校验后挂载到静态字段 */
    private static volatile AesKeyUtils INSTANCE;

    /** SecureRandom 实例（线程安全，无需额外同步） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 启动时校验密钥：缺失/非法 → 拒绝启动。
     * <p>
     * fail-fast 策略：宁可启动失败，也不要等到解密时报错才暴露密钥缺失。
     */
    @PostConstruct
    public void init() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "[AesKeyUtils] 启动失败：环境变量 " + KEY_PROPERTY
                            + " 未配置（AES 密钥用于 AI 模型 API Key 加密落库，启动拒绝）。");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "[AesKeyUtils] 启动失败：" + KEY_PROPERTY + " 不是合法 Base64 字符串。", e);
        }

        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "[AesKeyUtils] 启动失败：" + KEY_PROPERTY
                            + " 解码后长度必须为 32 字节（AES-256），实际=" + keyBytes.length + " bytes。");
        }

        // 校验通过 → 挂载到静态字段
        INSTANCE = this;
        secretKey = new SecretKeySpec(keyBytes, "AES");
        // 抹除本地数组，避免长期驻留
        Arrays.fill(keyBytes, (byte) 0);
        log.info("[AesKeyUtils] 初始化完成：{} 已配置（32 bytes AES-256-GCM）", KEY_PROPERTY);
    }

    /**
     * 加密任意明文字符串。返回拼接格式 {@code ciphertext || iv(12) || tag(16)}。
     *
     * @param plain 明文（API Key 等）
     * @return 加密字节数组
     */
    public static byte[] encrypt(String plain) {
        ensureReady();
        if (plain == null) {
            throw new IllegalArgumentException("[AesKeyUtils] encrypt: 明文不能为空");
        }
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainBytes);
            // 拼接：密文 || IV || tag（GCM 自动把 tag 附在末尾）
            byte[] result = new byte[cipherText.length + GCM_IV_LENGTH];
            System.arraycopy(cipherText, 0, result, 0, cipherText.length);
            System.arraycopy(iv, 0, result, cipherText.length, GCM_IV_LENGTH);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("[AesKeyUtils] 加密失败", e);
        }
    }

    /**
     * 解密 {@link #encrypt(String)} 产生的字节流。
     *
     * @param encrypted 加密字节（密文 || iv || tag）
     * @return 明文
     */
    public static String decrypt(byte[] encrypted) {
        ensureReady();
        if (encrypted == null || encrypted.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("[AesKeyUtils] decrypt: 密文长度不合法");
        }
        int cipherLen = encrypted.length - GCM_IV_LENGTH;
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encrypted, cipherLen, iv, 0, GCM_IV_LENGTH);
        byte[] cipherText = new byte[cipherLen];
        System.arraycopy(encrypted, 0, cipherText, 0, cipherLen);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("[AesKeyUtils] 解密失败（密钥错误 / 数据损坏）", e);
        }
    }

    /**
     * 生成 Key 的展示前缀（用于 UI 脱敏展示）：取前 {@code min(4, plain.length())} 个字符。
     *
     * @param plain 明文
     * @return 前缀（不超过 4 个字符）
     */
    public static String maskPrefix(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        int len = Math.min(4, plain.length());
        return plain.substring(0, len);
    }

    private static void ensureReady() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "[AesKeyUtils] 密钥未初始化——环境变量 " + KEY_PROPERTY + " 配置错误");
        }
    }

}