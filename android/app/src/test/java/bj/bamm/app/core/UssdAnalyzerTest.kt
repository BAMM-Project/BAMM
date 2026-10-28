package bj.bamm.app.core

import bj.bamm.app.core.UssdAnalyzer.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdAnalyzerTest {

    @Test fun `code dicte par le fraudeur = transfert`() {
        val r = UssdAnalyzer.analyze("*880*7721*45000#")
        assertEquals(Kind.TRANSFER, r.kind)
        assertEquals("880", r.service)
        assertEquals("7721", r.recipient)
        assertEquals(45000L, r.amount)
    }

    @Test fun `transfert vers un numero a 10 chiffres via le menu chiffre`() {
        val r = UssdAnalyzer.analyze("*880*1*1*0197123456*5000#")
        assertEquals(Kind.TRANSFER, r.kind)
        assertEquals("0197123456", r.recipient)
        assertEquals(5000L, r.amount)
    }

    @Test fun `Moov Money est aussi couvert`() {
        assertTrue(UssdAnalyzer.analyze("*855*66778899*10000#").isTransfer)
    }

    @Test fun `menu MoMo et navigation dans le menu ne sont pas des transferts`() {
        assertEquals(Kind.MENU, UssdAnalyzer.analyze("*880#").kind)
        assertEquals(Kind.MENU, UssdAnalyzer.analyze("*880*3*1#").kind)
    }

    @Test fun `codes courants non financiers`() {
        assertEquals(Kind.OTHER, UssdAnalyzer.analyze("*123#").kind)
        assertEquals(Kind.OTHER, UssdAnalyzer.analyze("*#06#").kind)
    }

    @Test fun `service inconnu avec numero et montant = transfert`() {
        assertTrue(UssdAnalyzer.analyze("*150*2*66778899*20000#").isTransfer)
    }

    @Test fun `numero de telephone classique`() {
        assertEquals(Kind.NOT_USSD, UssdAnalyzer.analyze("0197123456").kind)
        assertFalse(UssdAnalyzer.isCompleteUssd("*880*7721*450"))
        assertTrue(UssdAnalyzer.isCompleteUssd("*880*7721*45000#"))
    }

    @Test fun `diese encode dans une uri`() {
        assertTrue(UssdAnalyzer.analyze("*880*7721*45000%23").isTransfer)
    }
}
