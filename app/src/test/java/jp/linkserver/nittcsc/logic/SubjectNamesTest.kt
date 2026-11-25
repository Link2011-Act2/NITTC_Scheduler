package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test

class SubjectNamesTest {
    @Test fun romanGlyphsCompareWithoutChangingOtherCharacters() {
        assertTrue(sameSubject("英語II", "英語Ⅱ"))
        assertTrue(sameSubject("数学IV", "数学Ⅳ"))
        assertTrue(sameSubject("保体I", "保体Ⅰ"))
        assertFalse(sameSubject("英語I", "英語II"))
        assertFalse(sameSubject("数学１", "数学1"))
        assertTrue(subjectContains("数学Ⅳ演習", "数学IV"))
    }
}
