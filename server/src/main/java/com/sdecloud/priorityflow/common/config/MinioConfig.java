package com.sdecloud.priorityflow.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置：客户端 Bean + 启动时确保桶存在（附件预签名直传）。
 *
 * @author priority-flow
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "pf.minio")
public class MinioConfig {

    /** MinIO 服务地址（S3 API 端口，如 http://192.168.0.102:9000） */
    private String endpoint;

    /** 访问账号 */
    private String accessKey;

    /** 访问密钥 */
    private String secretKey;

    /** 附件桶名 */
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        // 启动时确保桶存在；MinIO 暂不可用时不阻断启动（附件接口在调用时报错）
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 附件桶：{}", bucket);
            } else {
                log.info("MinIO 附件桶已存在：{}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO 桶检查/创建失败（endpoint={}，bucket={}）：{}", endpoint, bucket, e.getMessage());
        }
        return client;
    }
}
