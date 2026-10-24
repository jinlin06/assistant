package org.example.assistant;

import org.example.assistant.agent.StudyTools;
import org.example.assistant.dashboard.LearningAnalyticsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 对话入口。
 *
 * <p>模型调用与业务逻辑完全解耦：本类只面向 ChatClient 链式 API，
 * 感知不到底层是 GLM / 通义 / DeepSeek 中的哪一个。
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final LearningAnalyticsService analyticsService;

    public ChatController(ChatClient assistantChatClient,
                          ChatMemory chatMemory,
                          LearningAnalyticsService analyticsService) {
        this.chatClient = assistantChatClient;
        this.chatMemory = chatMemory;
        this.analyticsService = analyticsService;
    }

    /**
     * 同步对话。
     * 记忆随取随用，服务重启不丢失。
     */
    @GetMapping("/api/chat")
        String answer = chatClient.prompt()
                .user(message)
                // 记忆与工具共享用户身份：RAG/记忆按 conversationId 隔离，工具端从 ToolContext 取 userId
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId)
                        .param(StudyTools.USER_ID_KEY, userId))
                .toolContext(Map.of(StudyTools.USER_ID_KEY, userId))
                .call()
                .content();

        // 旁路异步：把本轮对话交给大模型做结构化提取，供 Vue+ECharts 看板使用
        analyticsService.extractAndSaveAsync(userId, message, answer);
        return Map.of("answer", answer == null ? "" : answer);
    }

    /** 流式对话（SSE）：长回答边生成边推送，前端体验更好 */
    @GetMapping(value = "/api/chat/stream", produces = "text/stream;charset=UTF-8")
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId)
                        .param(StudyTools.USER_ID_KEY, userId))
                .toolContext(Map.of(StudyTools.USER_ID_KEY, userId))
                .stream()
                .content();
    }

    /** 清空某个用户的对话记忆（仪表盘「重置上下文」按钮） */
    @DeleteMapping("/api/chat/memory")
        chatMemory.clear(userId);
        return Map.of("message", "用户 " + userId + " 的对话记忆已清空");
    }

    /** 健康检查 + 当前接入模型提示（演示插拔式架构：换 Starter 即换模型） */
    @GetMapping("/api/model-info")
    public Map<String, String> modelInfo() {
        return Map.of(
                "provider", "zhipuai",
                "architecture", "Spring AI ChatClient -> 可插拔模型 Starter（当前：智谱 GLM）"
        );
    }
}
