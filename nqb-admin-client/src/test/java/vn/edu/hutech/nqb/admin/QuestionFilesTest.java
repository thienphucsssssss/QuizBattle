package vn.edu.hutech.nqb.admin;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestionFilesTest {
    @TempDir Path temp;
    static final List<String> VALUES=List.of("Giao thức nào tin cậy?\nChọn một đáp án.","TCP","UDP","IP","ARP","a","Lập trình mạng");
    static void excelFixture(Path file,List<List<String>> values)throws Exception {
        QuestionFiles.writeTemplate(file,QuestionFiles.Format.EXCEL);
        try(var in=Files.newInputStream(file);var book=new XSSFWorkbook(in)) {
            var sheet=book.getSheet("CauHoi");for(int r=0;r<values.size();r++){var row=sheet.getRow(r+1);if(row==null)row=sheet.createRow(r+1);for(int c=0;c<7;c++){var cell=row.getCell(c);if(cell==null)cell=row.createCell(c);cell.setCellValue(values.get(r).get(c));}}
            try(var out=Files.newOutputStream(file)){book.write(out);}
        }
    }
    @Test void generatedEmptyTemplatesRoundTripWithoutMockRows()throws Exception {
        for(var format:QuestionFiles.Format.values()){Path file=temp.resolve("template."+format.extension);QuestionFiles.writeTemplate(file,format);assertTrue(Files.size(file)>1000);assertEquals(0,QuestionFiles.readDirect(file).size());}
    }
    @Test void excelKeepsVietnameseMultilineAndFlagsEachBadRow()throws Exception {
        Path file=temp.resolve("questions.xlsx");var bad=new ArrayList<>(VALUES);bad.set(2,"");bad.set(5,"E");
        excelFixture(file,List.of(VALUES,bad));var rows=QuestionFiles.readDirect(file);assertEquals(2,rows.size());assertEquals("Dòng 2",rows.getFirst().source());assertEquals(VALUES.getFirst(),rows.getFirst().question().path("content").asText());assertEquals("A",rows.getFirst().question().path("correct_answer").asText());assertTrue(rows.getFirst().issues().isEmpty());assertFalse(rows.getLast().issues().isEmpty());
    }
    @Test void wordReadsTemplateTableAndIgnoresInstructionParagraphs()throws Exception {
        Path file=temp.resolve("questions.docx");QuestionFiles.writeTemplate(file,QuestionFiles.Format.WORD);
        try(var in=Files.newInputStream(file);var doc=new XWPFDocument(in)){for(int c=0;c<7;c++)doc.getTables().getFirst().getRow(1).getCell(c).setText(VALUES.get(c));try(var out=Files.newOutputStream(file)){doc.write(out);}}
        var rows=QuestionFiles.readDirect(file);assertEquals(1,rows.size());assertTrue(rows.getFirst().issues().isEmpty());assertEquals("Lập trình mạng",rows.getFirst().question().path("category").asText());
    }
    @Test void formulasAreNeverEvaluatedOrImported()throws Exception {
        Path file=temp.resolve("formula.xlsx");excelFixture(file,List.of(VALUES));
        try(var in=Files.newInputStream(file);var book=new XSSFWorkbook(in)){book.getSheet("CauHoi").getRow(1).getCell(1).setCellFormula("1+1");try(var out=Files.newOutputStream(file)){book.write(out);}}
        var row=QuestionFiles.readDirect(file).getFirst();assertTrue(row.issues().stream().anyMatch(s->s.contains("công thức")));assertEquals("",row.question().path("option_a").asText());
    }
    @Test void rejectsWrongHeaderLegacyAndMoreThan500Rows()throws Exception {
        Path file=temp.resolve("bad.xlsx");QuestionFiles.writeTemplate(file,QuestionFiles.Format.EXCEL);
        try(var in=Files.newInputStream(file);var book=new XSSFWorkbook(in)){book.getSheet("CauHoi").getRow(0).getCell(0).setCellValue("Renamed");try(var out=Files.newOutputStream(file)){book.write(out);}}
        assertThrows(java.io.IOException.class,()->QuestionFiles.readDirect(file));assertThrows(java.io.IOException.class,()->QuestionFiles.readDirect(temp.resolve("legacy.xls")));
        Path many=temp.resolve("many.xlsx");excelFixture(many,Collections.nCopies(501,VALUES));assertThrows(java.io.IOException.class,()->QuestionFiles.readDirect(many));
    }
    @Test void rejectsEmbeddedFilesZipBombAndOversizedInput()throws Exception {
        Path embed=temp.resolve("embed.docx");try(var zip=new ZipOutputStream(Files.newOutputStream(embed))){zip.putNextEntry(new ZipEntry("word/embeddings/payload.bin"));zip.write(1);zip.closeEntry();}assertThrows(java.io.IOException.class,()->QuestionFiles.readDirect(embed));
        Path bomb=temp.resolve("bomb.xlsx");try(var zip=new ZipOutputStream(Files.newOutputStream(bomb))){zip.putNextEntry(new ZipEntry("xl/large.xml"));byte[] block=new byte[8192];for(int i=0;i<1100;i++)zip.write(block);zip.closeEntry();}assertThrows(java.io.IOException.class,()->QuestionFiles.readDirect(bomb));
        Path big=temp.resolve("big.xlsx");try(var out=Files.newOutputStream(big)){byte[] block=new byte[1024];for(int i=0;i<5121;i++)out.write(block);}assertThrows(java.io.IOException.class,()->QuestionFiles.readSafely(big));
    }
    @Test void isolatedParserWorksWithBundledRuntimeClasspath()throws Exception {Path file=temp.resolve("worker.xlsx");excelFixture(file,List.of(VALUES));assertEquals(1,QuestionFiles.readSafely(file).size());}
    /** Opt-in export uses the same product code as the Save dialog, not a second generator. */
    public static void main(String[] args)throws Exception {Path output=Path.of(args[0]).toAbsolutePath();Files.createDirectories(output);for(var format:QuestionFiles.Format.values())QuestionFiles.writeTemplate(output.resolve("Mau_cau_hoi_QuizBattle."+format.extension),format);System.out.println("TEMPLATES_READY "+output);}
}
