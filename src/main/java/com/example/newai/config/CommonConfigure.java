package com.example.newai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
@Configuration
public class CommonConfigure {
    @Bean
    public JedisPooled jedisPooled() {
        return new JedisPooled("localhost", 6379);
        // 如需密码: new JedisPooled(new HostAndPort("localhost", 6379), DefaultJedisClientConfig.builder().password("yourpassword").build());
    }
    @Bean
    public ChatMemory chatMemory(){return MessageWindowChatMemory.builder().build();}
    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, OllamaEmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("spring-ai-index")
                .prefix("doc:")
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("file_name"),
                        RedisVectorStore.MetadataField.tag("source"),
                        RedisVectorStore.MetadataField.numeric("page"),
                        RedisVectorStore.MetadataField.tag("type")
                )
                .initializeSchema(true)  // 关键：设置为 true 自动创建索引
                .build();
    }
    @Bean
    public ChatClient chatClient(OllamaChatModel model, ChatMemory chatmemory) {
        return ChatClient.
                builder(model)
                .defaultSystem("你是台州学院助手")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatmemory).build()
                )
                .build();
    }
    @Bean
    public ChatClient pdfChatClient(OllamaChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient
                .builder(model)
                .defaultSystem("你是一个智能助手，请基于以下文档内容回答。遇到上下文没有的问题，不要随意编造。文档内容可能分布在多个片段中，请综合所有片段信息给出准确回答，不要遗漏。如果片段中未提及，请如实说明。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(
                                        SearchRequest.builder()
                                                .similarityThreshold(0.6)
                                                .topK(2)
                                                .build()
                                )
                                .build() // 最后调用 build()
                )
                .build();
    }

}
