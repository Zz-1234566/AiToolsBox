package com.example.aitools.support;

import com.example.aitools.dto.BatchFilePayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 证明「批量上传的临时文件生命周期」这个核心结论。
 *
 * <p><b>要证明的两件事</b>：
 * <ol>
 *   <li><b>请求结束后，Tomcat 的 multipart 临时文件已经没了</b> ——
 *       这正是「早期必须把文件内容读进内存」的原因：批量任务是异步跑的，
 *       等它开始处理时 Tomcat 早已回收临时文件，直接用 MultipartFile 会失败；</li>
 *   <li><b>我们自己的临时文件在请求结束后还在、还能完整读回</b> ——
 *       所以异步任务可以「按需读单个文件」，不必把整批字节塞进堆里。</li>
 * </ol>
 *
 * <p>这是「总大小上限能安全放宽到 200MB」的前提：
 * 解开了「必须整批读进内存」这个环，内存峰值才降得下来。
 */
@SpringBootTest(classes = MultipartLifecycleTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultipartLifecycleTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void tomcatTempFileIsGoneAfterRequestButOurOwnTempFileSurvives() throws Exception {
        byte[] content = new byte[5 * 1024 * 1024];
        Arrays.fill(content, (byte) 'A');

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "probe.bin";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resp = rest.exchange("/test/upload", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertEquals(200, resp.getStatusCodeValue(), "上传应成功");
        String ourTempPath = (String) resp.getBody().get("ourTempPath");
        assertNotNull(ourTempPath, "应返回我们自己的临时文件路径");

        // 请求已结束（HTTP 响应已收到），等容器回收完成
        Thread.sleep(500);

        // ---------- ① Tomcat 的 multipart 临时文件：请求结束后读不到 ----------
        MultipartFile tomcatFile = MultipartLifecycleTestApp.UploadController.lastUploadedFile;
        assertNotNull(tomcatFile, "应保留上传时的 MultipartFile 引用");
        String tomcatReadResult;
        try (var in = tomcatFile.getInputStream()) {
            byte[] b = in.readAllBytes();
            tomcatReadResult = "读到了 " + b.length + " 字节";
        } catch (Exception e) {
            tomcatReadResult = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("[multipart-lifecycle] 请求结束后读 Tomcat 的 MultipartFile → " + tomcatReadResult);

        // ---------- ② 我们自己的临时文件：还在，且能完整读回 ----------
        Path ours = Path.of(ourTempPath);
        assertTrue(Files.exists(ours), "我们自己的临时文件在请求结束后必须仍然存在（异步任务要靠它）");
        assertEquals(content.length, Files.size(ours), "临时文件内容应完整");
        byte[] readBack = BatchFilePayload.ofTempFile(ours, "probe.bin", null).getContent();
        assertEquals(content.length, readBack.length, "异步任务应能完整读回文件内容");
        System.out.println("[multipart-lifecycle] 请求结束后读我们自己的临时文件 → 成功，" + readBack.length + " 字节");

        // ---------- ③ 结论：异步任务不能依赖 Tomcat 的临时文件，但可以依赖我们自己的 ----------
        assertFalse(tomcatReadResult.startsWith("读到了"),
                "Tomcat 的 multipart 临时文件在请求结束后应当已被清理；"
                        + "若本断言失败，说明当前容器配置下 Tomcat 不清理，"
                        + "那么「异步任务直接用 MultipartFile」也可行 —— 结论需重新评估");
        assertTrue(Files.exists(ours), "方案不依赖 Tomcat 临时文件");

        Files.deleteIfExists(ours);
    }
}
