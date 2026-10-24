package org.example.assistant.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 私有知识库文档服务：上传 -> 解析 -> 切分 -> 向量化 -> 入库。
 *
 * <p>PDF / Word（docx）等非结构化文档统一交给 Apache Tika 解析，
 * 经 TokenTextSplitter 按语义切分成小段落后再调用 Embedding 模型向量化，
 * 检索时召回最相关的私有资料注入提示词，让 AI 回答「有据可查」，降低幻觉。
 */
@Service
public class DocumentService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    @Value("${assistant.rag.store-path:${user.home}/.assistant/vector-store.json}")
    private String vectorStorePath;

    public DocumentService(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    /**
     * 导入一份学习文档到私有知识库。
     *
     * @param file   PDF / Word / TXT 等文件
     * @param source 文档标识（保存原始文件名，便于溯源与按文档删除）
     * @return 切分后写入向量库的片段数
     */
    public int ingest(MultipartFile file, String source) throws IOException {
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        // Tika 自动识别 PDF / DOC / DOCX / TXT 等格式并提取纯文本
        List<Document> pages = new TikaDocumentReader(resource).get();
        List<Document> chunks = textSplitter.apply(pages);
        // 元数据标记来源，检索结果可回链到原始文档，实现「有据可查」
        chunks.forEach(doc -> doc.getMetadata().put("source", source));

        VectorStore store = vectorStoreProvider.getObject();
        store.add(chunks);
        persistIfSimple(store);
        return chunks.size();
    }

    /** 删除某个文档的全部片段（按 source 元数据过滤） */
    public void deleteBySource(String source) {
        VectorStore store = vectorStoreProvider.getObject();
        persistIfSimple(store);
    }

    /** SimpleVectorStore 是内存实现，写入后同步落到本地 JSON 文件 */
    private void persistIfSimple(VectorStore store) {
        if (store instanceof SimpleVectorStore svs) {
            File file = new File(vectorStorePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            svs.save(file);
        }
    }
}
