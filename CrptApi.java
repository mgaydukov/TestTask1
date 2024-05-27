import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final String API_KEY = "someApiKey";

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Gson gson;
    private final Semaphore requestSemaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.gson = new Gson();
        this.requestSemaphore = new Semaphore(requestLimit, true);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(this::resetRequestSemaphore, 0, 1, timeUnit);
    }

    public static class Document {
        private String description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;

        //Getters and Setters
    }

    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        //Getters and Setters
    }

    public void createDocument(Document document, String signature) throws InterruptedException {
        // Проверка количества запросов
        int availablePermits = requestSemaphore.availablePermits();
        if (availablePermits > requestLimit) {
            requestSemaphore.acquire(availablePermits - requestLimit);
        }
        requestSemaphore.acquire();

        // Создание запроса
        HttpClient client = HttpClient.newBuilder().build();
        String json = gson.toJson(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Отправка запроса и обработка ответа
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to create document: " + response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create document", e);
        } finally {
            requestSemaphore.release();
        }
    }

    private void resetRequestSemaphore() {
        int availablePermits = requestSemaphore.availablePermits();
        if (availablePermits > requestLimit) {
            requestSemaphore.release(availablePermits - requestLimit);
        }
        requestSemaphore.release(requestLimit);
    }

    public static void main(String[] args) throws InterruptedException {
        // Создаем экземпляр класса CrptApi с ограничением на 10 запросов в минуту
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

        // Создаем тестовый документ
        Document document = new Document();

        // Вызываем метод createDocument
        api.createDocument(document, "someSignature");
    }
}