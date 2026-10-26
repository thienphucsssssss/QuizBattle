package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** One bounded I/O queue per run. Successful append means the full JSON line was flushed. */
public final class LanRangeJournal {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path path;
    private final String origin,runId;
    private final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),
        Thread.ofPlatform().name("lan-range-journal").factory(),new ThreadPoolExecutor.AbortPolicy());
    private BufferedWriter output;
    private volatile Throwable failure;
    private CompletableFuture<Void> closing;
    public LanRangeJournal(Path directory,String origin,String runId) {
        if(!Set.of("client","server").contains(origin)||runId==null||!UUID.fromString(runId).toString().equals(runId))
            throw new IllegalArgumentException("Tên nhật ký không hợp lệ.");
        this.origin=origin;this.runId=runId;this.path=directory.resolve(origin+"-"+runId+".jsonl");
    }
    public Path path(){return path;}
    public synchronized CompletableFuture<Void> append(ObjectNode record) {
        if(closing!=null)return CompletableFuture.failedFuture(new IOException("Nhật ký đã đóng."));
        var future=new CompletableFuture<Void>();
        var copy=record.deepCopy().put("schema_version",1).put("origin",origin).put("run_id",runId).put("recorded_at",Instant.now().toString());
        try{writer.execute(()->{
            try{
                if(failure!=null)throw new IOException("Nhật ký gặp lỗi.",failure);
                if(output==null){Files.createDirectories(path.getParent());output=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
                output.write(JSON.writeValueAsString(copy));output.newLine();output.flush();future.complete(null);
            }catch(Exception error){failure=error;future.completeExceptionally(error);}
        });}catch(RejectedExecutionException error){failure=error;future.completeExceptionally(new IOException("Hàng đợi nhật ký đã đầy hoặc đã đóng.",error));}
        return future;
    }
    public synchronized CompletableFuture<Void> closeAsync() {
        if(closing!=null)return closing;
        closing=new CompletableFuture<>();writer.shutdown();
        Thread.ofVirtual().name("lan-range-journal-close").start(()->{
            try{
                if(!writer.awaitTermination(10,TimeUnit.SECONDS))throw new IOException("Hết thời gian lưu nhật ký.");
                if(output!=null)output.close();
                if(failure!=null)throw new IOException("Không lưu đủ nhật ký.",failure);
                closing.complete(null);
            }catch(Exception error){closing.completeExceptionally(error);}
        });
        return closing;
    }
    public static List<JsonNode> replay(Path file)throws IOException {
        if(Files.size(file)>2*1024*1024)throw new IOException("Nhật ký vượt giới hạn kích thước.");
        var lines=Files.readAllLines(file,StandardCharsets.UTF_8);var result=new ArrayList<JsonNode>();
        for(int i=0;i<lines.size();i++){
            if(lines.get(i).isBlank())continue;
            try{
                JsonNode node=JSON.readTree(lines.get(i));
                if(!node.isObject()||node.path("schema_version").asInt()!=1)throw new IOException("Sai định dạng nhật ký.");
                result.add(node);
            }catch(IOException error){if(i!=lines.size()-1)throw error;/* Preserve complete lines before a torn final record. */}
        }
        return List.copyOf(result);
    }
}
