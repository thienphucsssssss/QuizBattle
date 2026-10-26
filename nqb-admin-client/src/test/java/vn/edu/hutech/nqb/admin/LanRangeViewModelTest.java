package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanRangeViewModelTest {
    @Test void acceptsVietnameseDecimalInputButRejectsInventedOrInvalidDistances(){
        assertEquals("31.5",LanRangeView.parseDistance(" 31,5 ").toPlainString());
        assertEquals("0",LanRangeView.parseDistance("0").toPlainString());
        assertEquals("500",LanRangeView.parseDistance("500").toPlainString());
        for(String invalid:new String[]{"","-1","500.1","30.55","NaN","Infinity","3e1","30,5.5"})
            assertThrows(IllegalArgumentException.class,()->LanRangeView.parseDistance(invalid),invalid);
    }
}
