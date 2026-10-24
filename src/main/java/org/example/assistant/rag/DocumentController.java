package org.example.assistant.rag;

import org.springframework.http.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 私有知识库文档管理接口（供 Vue 前端「资料上传」面板调用）。
 */
@RestController
@Validated
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 上传学习文档（PDF/Word），解析并向量化入知识库 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") @NotNull MultipartFile file) throws IOException {
        String source = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        int chunks = documentService.ingest(file, source);
        return Map.of("source", source, "chunks", chunks, "message", "文档已入库，可直接提问");
    }

    /** 按文档名删除知识库中的全部片段 */
    @DeleteMapping
    public Map<String, Object> delete(@RequestParam @NotBlank String source) {
        documentService.deleteBySource(source);
        return Map.of("source", source, "message", "该文档的向量数据已删除");
    }
}
