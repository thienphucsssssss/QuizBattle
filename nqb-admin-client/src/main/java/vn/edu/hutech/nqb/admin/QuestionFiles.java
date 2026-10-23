package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import vn.edu.hutech.nqb.shared.*;
import java.io.*;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Local Office files only. Nothing is uploaded and no Office installation is required. */
final class QuestionFiles {
    static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final long MAX_FILE_BYTES=5L*1024*1024;
    record Draft(String source,ObjectNode question,List<String> issues) { }
    enum Format { EXCEL("xlsx"), WORD("docx"); final String extension; Format(String extension){this.extension=extension;} }
    private QuestionFiles() { }

    /** Parse untrusted XML/ZIP in a memory-limited child of the bundled Java runtime. */
    static List<Draft> readSafely(Path file) throws Exception {
        checkFile(file); Path temp=Files.createTempDirectory("nqb-question-import-");
        Path result=temp.resolve("result.json"),log=temp.resolve("diagnostic.txt");Process process=null;
        try {
            var cp=new LinkedHashSet<String>();cp.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))));
            for(ClassLoader loader=Thread.currentThread().getContextClassLoader();loader!=null;loader=loader.getParent())if(loader instanceof URLClassLoader urls)for(var url:urls.getURLs())if(url.getProtocol().equals("file"))cp.add(Path.of(url.toURI()).toString());
            String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
            process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",executable).toString(),"-Xmx160m","-XX:MaxMetaspaceSize=96m","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",String.join(File.pathSeparator,cp),QuestionFileWorker.class.getName(),file.toAbsolutePath().toString(),result.toString())
                .redirectOutput(log.toFile()).redirectErrorStream(true).start();
            if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Đọc tệp quá lâu. Hãy chia nhỏ tệp hoặc dùng lại mẫu mới.");}
            if(!Files.exists(result))throw new IOException("Không đọc được tệp. Hãy dùng mẫu Excel hoặc Word của QuizBattle.");
            if(Files.size(result)>3L*1024*1024)throw new IOException("Tệp có quá nhiều nội dung. Hãy chia nhỏ tệp.");
            JsonNode payload=JSON.readTree(Files.readString(result,StandardCharsets.UTF_8));
            if(payload==null||!payload.path("ok").asBoolean()||process.exitValue()!=0)throw new IOException(payload==null?"Không đọc được tệp. Hãy dùng mẫu Excel hoặc Word của QuizBattle.":payload.path("error").asText("Không đọc được tệp."));
            var rows=new ArrayList<Draft>();
            for(var row:payload.path("rows")){var issues=new ArrayList<String>();row.path("issues").forEach(v->issues.add(v.asText()));rows.add(new Draft(row.path("source").asText(),(ObjectNode)row.path("question"),List.copyOf(issues)));}
            return List.copyOf(rows);
        } finally {
            if(process!=null&&process.isAlive()){process.destroyForcibly();boolean interrupted=Thread.interrupted();try{process.waitFor(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){interrupted=true;}finally{if(interrupted)Thread.currentThread().interrupt();}}
            Files.deleteIfExists(result);Files.deleteIfExists(log);Files.deleteIfExists(temp);
        }
    }
    private static void checkFile(Path file)throws IOException {
        String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!name.endsWith(".xlsx")&&!name.endsWith(".docx"))throw new IOException("Chỉ nhận .xlsx hoặc .docx theo mẫu QuizBattle. Không nhận .xls, .doc, PDF hay ảnh.");
        if(!Files.isRegularFile(file)||Files.size(file)==0||Files.size(file)>MAX_FILE_BYTES)throw new IOException("Chọn tệp có dữ liệu, dung lượng tối đa 5 MiB.");
    }
    static List<Draft> readDirect(Path file)throws Exception {
        checkFile(file);checkArchive(file);var rows=new ArrayList<Draft>();
        if(file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))readExcel(file,rows);else readWord(file,rows);
        int bytes=0;for(var row:rows)bytes+=row.question().toString().getBytes(StandardCharsets.UTF_8).length+2;
        if(bytes>QuestionImportRules.MAX_JSON_BYTES)throw new IOException("Nội dung vượt 2 MiB. Hãy chia nhỏ tệp.");return List.copyOf(rows);
    }
    private static void checkArchive(Path file)throws IOException {
        long total=0;int count=0;
        try(var zip=new ZipFile(file.toFile())) {
            var entries=zip.entries();byte[] buffer=new byte[8192];
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement();String name=entry.getName().toLowerCase(Locale.ROOT);
                if(++count>200||name.contains("vbaproject")||name.contains("/embeddings/")||name.contains("/externallinks/")||name.contains("/media/"))throw new IOException("Mẫu nhập chỉ hỗ trợ văn bản, không nhận macro, tệp nhúng, liên kết ngoài hoặc hình ảnh.");
                if(entry.isDirectory())continue;long part=0;
                try(var in=zip.getInputStream(entry)){int n;while((n=in.read(buffer))!=-1){part+=n;total+=n;if(part>8L*1024*1024||total>24L*1024*1024)throw new IOException("Tệp quá lớn sau giải nén. Hãy chia nhỏ tệp.");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}}
            }
        } catch(java.util.zip.ZipException invalid){throw new IOException("Tệp không đúng định dạng Office. Hãy lưu lại thành .xlsx hoặc .docx không đặt mật khẩu.");}
    }
    private static void readExcel(Path file,List<Draft> rows)throws Exception {
        try(var in=Files.newInputStream(file);var book=new XSSFWorkbook(in)) {
            Sheet sheet=book.getSheet("CauHoi");
            if(sheet==null)throw new IOException("Không thấy trang CauHoi. Hãy dùng mẫu Excel tải từ QuizBattle.");
            var formatter=new DataFormatter(Locale.ROOT);var headers=new ArrayList<String>();
            for(int i=0;i<7;i++)headers.add(sheet.getRow(0)==null?"":formatter.formatCellValue(sheet.getRow(0).getCell(i)));
            requireHeaders(headers);
            for(var row:sheet) {
                if(row.getRowNum()==0)continue;var values=new ArrayList<String>();var issues=new ArrayList<String>();
                for(int i=0;i<7;i++){Cell cell=row.getCell(i);if(cell!=null&&(cell.getCellType()==CellType.FORMULA||cell.getCellType()==CellType.ERROR)){issues.add(QuestionImportRules.HEADERS.get(i)+": thay công thức/lỗi bằng văn bản.");values.add("");}else values.add(cell==null?"":formatter.formatCellValue(cell));}
                append(rows,"Dòng "+(row.getRowNum()+1),values,issues);
            }
        }
    }
    private static void readWord(Path file,List<Draft> rows)throws Exception {
        try(var in=Files.newInputStream(file);var document=new XWPFDocument(in)) {
            int matched=0,tableNumber=0;
            for(var table:document.getTables()) {
                tableNumber++;if(table.getRows().isEmpty())continue;var first=table.getRow(0).getTableCells().stream().map(XWPFTableCell::getText).toList();
                if(first.size()!=7||!headersMatch(first))continue;matched++;
                for(int r=1;r<table.getNumberOfRows();r++) {
                    var cells=table.getRow(r).getTableCells();if(cells.size()!=7)throw new IOException("Bảng "+tableNumber+", dòng "+(r+1)+": cần đúng 7 ô, không gộp ô.");
                    append(rows,"Bảng "+tableNumber+" · dòng "+(r+1),cells.stream().map(XWPFTableCell::getText).toList(),new ArrayList<>());
                }
            }
            if(matched==0)throw new IOException("Không tìm thấy bảng câu hỏi đúng 7 cột. Hãy dùng mẫu Word tải từ QuizBattle.");
        }
    }
    private static String headerKey(String text){return Normalizer.normalize(text.strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replaceAll("\\s+"," ");}
    private static boolean headersMatch(List<String> headers){for(int i=0;i<7;i++)if(!headerKey(headers.get(i)).equals(headerKey(QuestionImportRules.HEADERS.get(i)))&&!headers.get(i).strip().equals(QuestionImportRules.KEYS.get(i)))return false;return true;}
    private static void requireHeaders(List<String> headers)throws IOException {if(!headersMatch(headers))throw new IOException("Giữ nguyên 7 cột theo thứ tự: Câu hỏi, A, B, C, D, Đáp án đúng, Chủ đề.");}
    private static void append(List<Draft> rows,String source,List<String> values,List<String> issues)throws IOException {
        if(issues.isEmpty()&&values.stream().allMatch(String::isBlank))return;
        if(rows.size()>=QuestionImportRules.MAX_ROWS)throw new IOException("Mỗi tệp tối đa 500 câu. Hãy chia thành nhiều tệp.");
        var question=JSON.createObjectNode();for(int i=0;i<7;i++)question.put(QuestionImportRules.KEYS.get(i),values.get(i));
        issues.addAll(QuestionImportRules.errors(question));rows.add(new Draft(source,QuestionImportRules.normalize(question),List.copyOf(issues)));
    }
    static void writeTemplate(Path target,Format format)throws Exception {
        Path absolute=target.toAbsolutePath(),temp=Files.createTempFile(absolute.getParent(),".quizbattle-template-","."+format.extension);
        try {if(format==Format.EXCEL)writeExcel(temp);else writeWord(temp);try{Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temp);}
    }
    private static final List<String> INSTRUCTIONS=List.of("Điền một câu hỏi trên mỗi dòng; giữ nguyên tên và thứ tự 7 cột.","Điền đủ bốn lựa chọn A, B, C, D. Cột Đáp án đúng chỉ ghi một chữ A, B, C hoặc D.","Câu hỏi tối đa 2.000 ký tự; mỗi lựa chọn tối đa 500; chủ đề tối đa 80.","Tối đa 500 câu và 5 MiB mỗi tệp. Chỉ nhập văn bản, không dùng công thức, hình ảnh hay ô gộp.","Dòng trống được bỏ qua. QuizBattle cho xem trước, báo lỗi và báo trùng trước khi lưu.");
    private static void writeExcel(Path file)throws Exception {
        try(var book=new XSSFWorkbook()) {
            var sheet=book.createSheet("CauHoi");sheet.createFreezePane(0,1);sheet.setDisplayGridlines(false);
            var font=book.createFont();font.setFontName("Arial");font.setFontHeightInPoints((short)11);
            var body=book.createCellStyle();body.setFont(font);body.setWrapText(true);body.setVerticalAlignment(VerticalAlignment.TOP);body.setDataFormat(book.createDataFormat().getFormat("@"));
            var headerFont=book.createFont();headerFont.setFontName("Arial");headerFont.setBold(true);headerFont.setColor(IndexedColors.WHITE.getIndex());
            var header=book.createCellStyle();header.cloneStyleFrom(body);header.setFont(headerFont);header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var line=sheet.createRow(0);line.setHeightInPoints(32);
            for(int c=0;c<7;c++){var cell=line.createCell(c);cell.setCellValue(QuestionImportRules.HEADERS.get(c));cell.setCellStyle(header);sheet.setColumnWidth(c,(c==0?44:c==5?18:c==6?22:26)*256);sheet.setDefaultColumnStyle(c,body);}
            for(int r=1;r<=10;r++){var row=sheet.createRow(r);row.setHeightInPoints(32);for(int c=0;c<7;c++)row.createCell(c).setCellStyle(body);}
            var helper=sheet.getDataValidationHelper();var validation=helper.createValidation(helper.createExplicitListConstraint(new String[]{"A","B","C","D"}),new org.apache.poi.ss.util.CellRangeAddressList(1,500,5,5));validation.setShowErrorBox(true);validation.createErrorBox("Đáp án đúng","Chỉ chọn A, B, C hoặc D.");sheet.addValidationData(validation);
            var guide=book.createSheet("HuongDan");guide.setDisplayGridlines(false);guide.setColumnWidth(0,110*256);
            var title=guide.createRow(0);title.setHeightInPoints(32);title.createCell(0).setCellValue("Mẫu câu hỏi QuizBattle");title.getCell(0).setCellStyle(header);
            for(int i=0;i<INSTRUCTIONS.size();i++){var row=guide.createRow(i+2);row.setHeightInPoints(38);var cell=row.createCell(0);cell.setCellValue(INSTRUCTIONS.get(i));cell.setCellStyle(body);}
            try(var out=Files.newOutputStream(file)){book.write(out);}
        }
    }
    private static void writeWord(Path file)throws Exception {
        try(var document=new XWPFDocument()) {
            var section=document.getDocument().getBody().addNewSectPr();var size=section.addNewPgSz();size.setW(BigInteger.valueOf(12240));size.setH(BigInteger.valueOf(15840));
            var margin=section.addNewPgMar();margin.setTop(BigInteger.valueOf(1080));margin.setBottom(BigInteger.valueOf(1080));margin.setLeft(BigInteger.valueOf(1440));margin.setRight(BigInteger.valueOf(1440));
            var title=document.createParagraph();title.setStyle("Title");var run=title.createRun();run.setFontFamily("Arial");run.setFontSize(22);run.setBold(true);run.setColor("000000");run.setText("Mẫu câu hỏi QuizBattle");title.setSpacingAfter(180);
            for(String instruction:INSTRUCTIONS){var paragraph=document.createParagraph();paragraph.setSpacingAfter(100);var r=paragraph.createRun();r.setFontFamily("Arial");r.setFontSize(10);r.setText(instruction);}
            var table=document.createTable(4,7);table.setWidth("100%");int[] widths={2500,1120,1120,1120,1120,1040,1240};
            var props=table.getCTTbl().getTblPr();var layout=props.isSetTblLayout()?props.getTblLayout():props.addNewTblLayout();layout.setType(STTblLayoutType.FIXED);
            var grid=table.getCTTbl().getTblGrid();if(grid==null)grid=table.getCTTbl().addNewTblGrid();while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);for(int width:widths)grid.addNewGridCol().setW(BigInteger.valueOf(width));
            table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"D9D9D9");table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"D9D9D9");
            table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"D9D9D9");table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"D9D9D9");table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"D9D9D9");table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"D9D9D9");table.setCellMargins(100,85,100,85);
            for(int r=0;r<4;r++) {
                var row=table.getRow(r);if(r==0)row.setRepeatHeader(true);else row.setHeight(850);
                for(int c=0;c<7;c++){var cell=row.getCell(c);cell.setWidth(Integer.toString(widths[c]));var paragraph=cell.getParagraphs().getFirst();paragraph.setSpacingAfter(0);var text=paragraph.createRun();text.setFontFamily("Arial");text.setFontSize(10);if(r==0){cell.setColor("243C81");text.setColor("FFFFFF");text.setBold(true);text.setText(QuestionImportRules.HEADERS.get(c));}}
            }
            try(var out=Files.newOutputStream(file)){document.write(out);}
        }
    }
}
