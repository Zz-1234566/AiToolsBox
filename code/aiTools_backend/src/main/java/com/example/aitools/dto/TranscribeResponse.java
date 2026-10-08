package com.example.aitools.dto;

/**
 * 录音转文本响应 DTO
 * <p>
 * 包装一句话识别的识别文本结果
 */
public class TranscribeResponse {

    private String text;

    private Integer duration;  // 秒（可选）

    public TranscribeResponse() {}

    public TranscribeResponse(String text, Integer duration) {
        this.text = text;
        this.duration = duration;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
