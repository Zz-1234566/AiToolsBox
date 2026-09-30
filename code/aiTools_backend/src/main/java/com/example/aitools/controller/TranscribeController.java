package com.example.aitools.controller;

import com.example.aitools.common.ResultCode;
import com.example.aitools.common.Result;
import com.example.aitools.dto.TranscribeResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.TranscribeService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 录音转文本 Controller
 * <p>
 * 接收上传的音频文件（mp3/wav/m4a），调腾讯云一句话识别，返回识别文本
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-office/meeting-minutes")
@RequiredArgsConstructor
public class TranscribeController {

    private final TranscribeService transcribeService;
    private final AuthUtil authUtil;

    @PostMapping(value = "/transcribe", consumes = "multipart/form-data")
    public Result<TranscribeResponse> transcribe(@RequestParam("file") MultipartFile file,
                                                HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        log.info("[meeting-minutes/transcribe] userId={} file={} size={}",
                userId, file.getOriginalFilename(), file.getSize());
        try {
            TranscribeResponse resp = transcribeService.transcribe(file);
            return Result.success("转写成功", resp);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[meeting-minutes/transcribe] failed", e);
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        }
    }
}
