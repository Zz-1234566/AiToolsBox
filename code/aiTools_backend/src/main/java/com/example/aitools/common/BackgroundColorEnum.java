package com.example.aitools.common;

import com.example.aitools.exception.BusinessException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 证件照底色枚举（证件红 / 证件蓝 / 白底）。
 * <p>
 * 只开放证件照标准三色，不做「任意取色」：前端传十六进制会让非法值（如透明色、
 * 与人像撞色的深色）一路走到合成阶段才炸，错误难定位。这里把取值收敛成闭集，
 * 非法值在<b>入口</b>就抛业务异常，错误信息面向用户。
 * <p>
 * 蓝色取 {@code #438EDB} 而非纯蓝：这是二代身份证 / 护照照片的官方标准蓝，
 * 纯蓝（#0000FF）偏紫，打印后偏色明显。
 */
public enum BackgroundColorEnum {

    /** 证件红 */
    RED("red", "红色", "#FF0000"),

    /** 证件蓝（二代证标准蓝） */
    BLUE("blue", "蓝色", "#438EDB"),

    /** 白底（用于生活照/白底二寸） */
    WHITE("white", "白色", "#FFFFFF");

    /** 未指定底色时的默认值 */
    public static final String DEFAULT_CODE = "red";

    private final String code;
    private final String displayName;
    private final String hex;

    BackgroundColorEnum(String code, String displayName, String hex) {
        this.code = code;
        this.displayName = displayName;
        this.hex = hex;
    }

    public String getCode() {
        return code;
    }

    /** 中文名，如「红色」 */
    public String getDisplayName() {
        return displayName;
    }

    /** 十六进制色值，如 {@code #FF0000} */
    public String getHex() {
        return hex;
    }

    /** 解析为 RGB 三元组（0~255），避免每处调用点重复写 Color.decode */
    public int[] toRgb() {
        return new int[]{
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    /**
     * 按 code 解析底色。
     * <p>
     * null / 空白按默认值（红）处理 —— 前端不传 bgColor 时不该报错；
     * 非空但非法则抛业务异常，提示里列出全部可选值。
     *
     * @param code 前端传入的底色编码，如 red / blue / white（大小写不敏感）
     * @return 底色枚举；入参为空时返回默认红色
     * @throws BusinessException 传入非法底色值
     */
    public static BackgroundColorEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return RED;
        }
        String trimmed = code.trim();
        for (BackgroundColorEnum e : values()) {
            if (e.code.equalsIgnoreCase(trimmed)) {
                return e;
            }
        }
        String supported = Arrays.stream(values())
                .map(BackgroundColorEnum::getCode)
                .collect(Collectors.joining(" / "));
        throw new BusinessException(ResultCode.SEGMENT_BAD_COLOR.getCode(),
                "不支持的底色：" + trimmed + "，仅支持 " + supported);
    }
}