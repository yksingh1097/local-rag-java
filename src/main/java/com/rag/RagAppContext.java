package com.rag;

import java.time.Duration;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;
import dev.langchain4j.service.SystemMessage;

public class RagAppContext {

    

interface Assistant {
    @SystemMessage("""
	Answer ONLY using the provided context below.
	If the context does not contain the answer, reply exactly:
	"Not found in the provided documents."
	Do not use any outside knowledge. Do not invent book titles.
	""")
    String chat(String userMessage);
}

    public static void main(String[] args) throws Exception {

        String pdfPath = "src/main/resources/docs/Good_Vibes_Good_Life.pdf";

        // 1. Read the PDF into plain text
        System.out.println("Loading PDF...");
        InputStream stream = new FileInputStream(pdfPath);
        Document document = new ApachePdfBoxDocumentParser().parse(stream);
        System.out.println("Loaded " + document.text().length() + " characters");

        // 2. Split into chunks. THESE TWO NUMBERS ARE THE INTERESTING PART.
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);

        // 3. Embedding model runs inside this JVM, not through Ollama
        System.out.println("Loading embedding model...");
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // 4. In-memory vector store
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // 5. Chunk + embed + store. This is the slow step.
        System.out.println("Embedding chunks (this takes a while on CPU)...");
        long start = System.currentTimeMillis();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(document);
        System.out.println("Ingestion took " + (System.currentTimeMillis() - start) / 1000 + "s");

        // 6. Connect to your local Ollama
	ChatModel chatModel = OllamaChatModel.builder()
		.baseUrl("http://localhost:11434")
		.modelName("qwen2.5:1.5b")
		.temperature(0.0)
		.timeout(Duration.ofMinutes(10))
		.build();

        // 7. Wire retrieval + generation together
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(2)
                        .build())
                .build();

        // 8. Ask questions
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nQuestion (or 'exit'): ");
            String q = scanner.nextLine();
            if (q.equalsIgnoreCase("exit")) break;
            System.out.println("\n" + assistant.chat(q));
        }
        scanner.close();
    }
}