package org.example.assistant.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentService 单元测试：聚焦删除逻辑的安全修复——
 * 必须走参数化 Filter.Expression，而非把文件名直接拼进过滤字符串。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private ObjectProvider<VectorStore> vectorStoreProvider;

    @Mock
    private VectorStore vectorStore;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(vectorStoreProvider);
        when(vectorStoreProvider.getObject()).thenReturn(vectorStore);
    }

    @Test
    void deleteBySource_usesParameterizedFilterExpression() {
        // 文件名含单引号：字符串拼接会破坏表达式，参数化 builder 则安全转义
        String trickySource = "报告'2026\".pdf";
        documentService.deleteBySource(trickySource);

        // 关键断言：删除走的是参数化 Filter.Expression 重载，而非拼接字符串重载
        ArgumentCaptor<Filter.Expression> captor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        // 全链依赖 source 等值匹配，表达式中不应出现未转义的文件名拼接痕迹
        assertThat(captor.getValue().toString()).contains("source");
    }

    @Test
    void deleteBySource_neverCallsStringOverload() {
        documentService.deleteBySource("any.pdf");
        // 断言走的是 Expression 重载：String 重载不应被调用
        verify(vectorStore, org.mockito.Mockito.never()).delete(any(String.class));
    }
}
