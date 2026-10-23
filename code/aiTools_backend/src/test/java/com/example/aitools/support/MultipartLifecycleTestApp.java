package com.example.aitools.support;

import com.example.aitools.dto.BatchFilePayload;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 专供 {@link MultipartLifecycleTest} 使用的最小应用（不连数据库）。
 */
@SpringBootApplication
public class MultipartLifecycleTestApp {

    @RestController
    public static class UploadController {

        /** 保留上传时的 MultipartFile 引用：请求结束后再读一次，
         *  用来观察「Tomcat 清理临时文件」对异步任务意味着什么。 */
        public static volatile MultipartFile lastUploadedFile;

        /**
         * 模拟真实批量上传端点：把上传文件**流式复制到我们自己的临时文件**，
         * 返回该临时文件路径。异步任务稍后读这个文件。
         */
        @PostMapping("/test/upload")
        public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws Exception {
            lastUploadedFile = file;
            Path dir = BatchFilePayload.ensureTempDir();
            Path temp = dir.resolve("it-" + System.nanoTime());
            BatchFilePayload.copyToFile(file, temp);

            Map<String, Object> resp = new HashMap<>();
            resp.put("ourTempPath", temp.toAbsolutePath().toString());
            resp.put("ourTempSize", Files.size(temp));
            // Tomcat 侧的临时文件（Spring 的 StandardMultipartFile 内部持有）
            resp.put("tomcatTempFileName", describeTomcatTempFile());
            return resp;
        }

        /**
         * 尽力找出 Servlet 容器为本次上传创建的临时文件（Tomcat 的 upload_*.tmp）。
         * 拿不到就返回 null（不影响测试结论 —— 结论靠目录快照 diff）。
         */
        private String describeTomcatTempFile() {
            try {
                Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
                try (var s = Files.list(tmp)) {
                    return s.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("upload_") || n.startsWith("spring_multipart")
                                || n.startsWith("tomcat");
                    }).map(p -> p.getFileName().toString()).sorted().findFirst().orElse(null);
                }
            } catch (Exception e) {
                return null;
            }
        }
    }
}
