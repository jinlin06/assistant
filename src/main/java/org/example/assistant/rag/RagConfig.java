package org.example.assistant.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.File;

/**
 * 向量库配置。
 *
 * <p>开发环境使用 Spring AI 内置的 SimpleVectorStore（内存 + 本地 JSON 持久化），
 * 生产环境可无缝替换为 Redis / Milvus / Qdrant 等向量库 Starter，
 * 业务代码只依赖 {@link VectorStore} 抽象，体现插拔式架构。
 */
@Configuration
public class RagConfig {

    @Value("${assistant.rag.store-path:${user.home}/.assistant/vector-store.json}")
    private String vectorStorePath;

    @Bean
    @Lazy
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(vectorStorePath);
        if (file.exists()) {
            store.load(file);
        }
        return store;
    }
}
