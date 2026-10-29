package vn.edu.hutech.nqb.admin;

import java.util.Locale;

/** Presentation only; milliseconds and streaks always come from Server. */
final class AnswerMeta {
    private AnswerMeta() { }
    static String time(long millis){return millis<0?"":String.format(Locale.forLanguageTag("vi-VN"),"%.2f s",millis/1000d);}
    static String streak(int count){return count<2?"":"Chuỗi ×"+count;}
}
