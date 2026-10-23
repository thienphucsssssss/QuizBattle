package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionImportRulesTest {
    private com.fasterxml.jackson.databind.node.ObjectNode row(){var q=JsonNodeFactory.instance.objectNode();QuestionImportRules.KEYS.forEach(k->q.put(k,"Text"));return q.put("correct_answer"," a ");}
    @Test void normalizesAndWhitelistsWithoutAcceptingOwnerOrIds(){var q=row().put("owner_user_id",99).put("question_id",1).put("allow_duplicate",true);assertTrue(QuestionImportRules.errors(q).isEmpty());var normalized=QuestionImportRules.normalize(q);assertEquals("A",normalized.path("correct_answer").asText());assertFalse(normalized.has("owner_user_id"));assertFalse(normalized.has("question_id"));assertFalse(normalized.has("allow_duplicate"));assertTrue(normalized.path("is_active").asBoolean());}
    @Test void validatesTextLimitsTypesAndControls(){assertFalse(QuestionImportRules.errors(row().put("option_c"," ")).isEmpty());assertFalse(QuestionImportRules.errors(row().put("content","x".repeat(2001))).isEmpty());assertFalse(QuestionImportRules.errors(row().put("category",123)).isEmpty());assertFalse(QuestionImportRules.errors(row().put("content","a\u0000b")).isEmpty());assertFalse(QuestionImportRules.errors(row().put("correct_answer","1")).isEmpty());assertTrue(QuestionImportRules.errors(row().put("content","Tiếng Việt\nDòng 2")).isEmpty());}
}
