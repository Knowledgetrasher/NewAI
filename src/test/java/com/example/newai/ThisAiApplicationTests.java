package com.example.newai;

import com.example.newai.repository.FileRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor.FILTER_EXPRESSION;

@SpringBootTest
public class ThisAiApplicationTests {
    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private ChatClient pdfChatClient;

    @Autowired
    private FileRepository fileRepository;

    @Test
    void ragSearchReturnsRelevantChunk() {
        Resource resource = new FileSystemResource("人工智能学院研究生制度汇编.pdf");
        Assumptions.assumeTrue(resource.exists());
        String chatId = UUID.randomUUID().toString();
        boolean saved = fileRepository.save(chatId, resource);
        assertTrue(saved);
        List<Document> documents = readAndSplit(resource);
        vectorStore.add(documents);
        String filename = resource.getFilename();
        String escapedFilename = filename == null ? "" : filename.replaceAll("\\.", "\\\\.");
        long searchStart = System.nanoTime();
        SearchRequest request = SearchRequest.builder()
                .query("台州学院全日制学术学位硕士学费多少？")
                .topK(2)
                .similarityThreshold(0.6)
                .filterExpression("file_name == '" + escapedFilename + "'")
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        long searchEnd = System.nanoTime();
        System.out.println("向量检索耗时(ms): " + (searchEnd - searchStart) / 1_000_000);
        assertNotNull(docs);
        assertFalse(docs.isEmpty());
        assertTrue(docs.stream().anyMatch(doc -> doc.getText() != null && doc.getText().length() >= 20));
    }

    @Test
    void pdfChatUsesRagContext() {
        Resource resource = new FileSystemResource("人工智能学院研究生制度汇编.pdf");
        Assumptions.assumeTrue(resource.exists());
        String chatId = UUID.randomUUID().toString();
        boolean saved = fileRepository.save(chatId, resource);
        assertTrue(saved);
        List<Document> documents = readAndSplit(resource);
        vectorStore.add(documents);
        String filename = resource.getFilename();
        String escapedFilename = filename == null ? "" : filename.replaceAll("\\.", "\\\\.");
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(
                        SearchRequest.builder()
                                .similarityThreshold(0.6)
                                .topK(2)
                                .build()
                )
                .build();
        long inferStart = System.nanoTime();
        Flux<String> stream = pdfChatClient.prompt()
                .user("台州学院全日制学术学位硕士学费多少？")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(a -> a.param(FILTER_EXPRESSION, "file_name == '" + escapedFilename + "'"))
                .advisors(advisor)
                .stream()
                .content();
        List<String> responses = stream.collectList().block();
        long inferEnd = System.nanoTime();
        System.out.println("推理耗时(ms): " + (inferEnd - inferStart) / 1_000_000);
        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        String merged = String.join("", responses).trim();
        assertFalse(merged.isBlank());
    }

    @Test
    void redisConnectionIsAvailable() {
        long connectStart = System.nanoTime();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            long connectEnd = System.nanoTime();
            System.out.println("Redis连接耗时(ms): " + (connectEnd - connectStart) / 1_000_000);
            assertNotNull(pong);
        }
    }

    private List<Document> readAndSplit(Resource resource) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1)
                        .build()
        );
        List<Document> rawDocuments = reader.read();
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(rawDocuments);
    }
}
