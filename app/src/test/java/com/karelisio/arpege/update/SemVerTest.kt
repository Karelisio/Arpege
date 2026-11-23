package com.karelisio.arpege.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test fun parsesWithPrefix() = assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
    @Test fun rejectsGarbage() = assertNull(SemVer.parse("1.2"))
    @Test fun compares() {
        assertTrue(SemVer.parse("0.10.0")!! > SemVer.parse("0.9.9")!!)
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("0.99.99")!!)
    }
}
