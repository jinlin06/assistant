package org.example.assistant.config;

import org.example.assistant.agent.StudyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 统一装配入口 —— 大模型接入与架构解耦的核心。
 *
 * <p>业务代码只面向 Spring AI 的 {@link ChatClient} 抽象编程，
 * 底层模型由 Starter（当前为 spring-ai-starter-model-zhipuai）决定；
 * 若要切换到通义千问 / DeepSeek 等，只需替换一个 Starter 依赖并改两行配置，
 * 本类及所有 Controller / Service 代码零改动，实现插拔式替换。
 *
 * <p>链式装配的三个能力：
 * <ol>
 *   <li>MessageChatMemoryAdvisor：滑动窗口 20 条的持久化对话记忆（MySQL）；</li>
 *   <li>QuestionAnswerAdvisor：召回私有知识库文档片段（RAG）；</li>
 *   <li>defaultTools：注册 Agent 工具（学习任务 / 日程）。</li>
 * </ol>
 */
@Configuration
public class ChatClientConfig {

    /** 滑动窗口大小：只为模型保留最近 20 条上下文，防止 Token 超限 */
    private static final int MEMORY_WINDOW_SIZE = 20;

    /** RAG 召回条数与相似度阈值 */
    private static final int RAG_TOP_K = 4;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.5;

    /**
     * 对话记忆：MessageWindowChatMemory + 自定义 MySQL ChatMemoryRepository。
     * 只要容器中存在 {@link ChatMemoryRepository} 实现，
     * MessageChatMemoryAdvisor 就会经由它把上下文持久化到 MySQL。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MEMORY_WINDOW_SIZE)
                .build();
    }

    /** 主对话客户端：记忆 + RAG + Agent 工具 */
    @Bean
    public ChatClient assistantChatClient(ChatClient.Builder builder,
                                          ChatMemory chatMemory,
                                          ObjectProvider<VectorStore> vectorStoreProvider,
                                          StudyTools studyTools) {
        var spec = builder
                .defaultSystem("""
                        你是一名私人学习助手。回答要求：
                        1. 优先依据「 context 」中的私有资料作答，引用时说明来源文档；
                        2. 若资料中没有依据，明确告知不确定，绝不编造（降低幻觉）；
                        3. 回答使用简体中文，结构清晰、循序渐进。
                        """)
                // 多用户持久化记忆：conversationId 运行时按用户传入
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                // Agent 工具：自然语言 -> 意图识别 -> 自动执行
                .defaultTools(studyTools);

        // RAG 检索增强：向量库懒加载（未上传文档时不影响启动与对话）
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            spec = spec.defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder()
                            .topK(RAG_TOP_K)
                            .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                            .build())
                    .build());
        }
        return spec.build();
    }

    /**
     * 学情分析专用客户端：不挂任何 Advisor。
     * 若复用主客户端做结构化提取，分析指令与其 JSON 回复会被写进
     * MessageChatMemoryAdvisor 的用户对话记忆，污染上下文，故物理隔离。
     */
    @Bean
    public ChatClient analyticsChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
