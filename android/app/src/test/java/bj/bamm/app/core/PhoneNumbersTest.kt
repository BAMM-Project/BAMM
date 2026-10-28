package bj.bamm.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mêmes cas que `backend/tests/test_api.py::test_normalize` : les deux côtés doivent concorder. */
class PhoneNumbersTest {
    @Test fun normalize() {
        assertEquals("0197123456", PhoneNumbers.normalize("+229 01 97 12 34 56"))
        assertEquals("0197123456", PhoneNumbers.normalize("00229 0197123456"))
        assertEquals("9797001", PhoneNumbers.normalize("97 97 001"))
        assertEquals("", PhoneNumbers.normalize("abc"))
    }
}
