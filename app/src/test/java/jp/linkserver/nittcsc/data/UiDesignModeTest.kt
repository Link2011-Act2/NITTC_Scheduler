package jp.linkserver.nittcsc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UiDesignModeTest {

    @Test
    fun missingStoredValueDefaultsToMaterial3() {
        assertEquals(UiDesignMode.MATERIAL_3, UiDesignMode.fromStoredValue(null))
    }

    @Test
    fun invalidStoredValueDefaultsToMaterial3() {
        assertEquals(UiDesignMode.MATERIAL_3, UiDesignMode.fromStoredValue("NOT_A_DESIGN_MODE"))
    }

    @Test
    fun storedExpressiveValueIsRestored() {
        assertEquals(
            UiDesignMode.MATERIAL_3_EXPRESSIVE,
            UiDesignMode.fromStoredValue(UiDesignMode.MATERIAL_3_EXPRESSIVE.name)
        )
    }

    @Test
    fun unavailableExpressiveModeFallsBackToMaterial3() {
        assertEquals(
            UiDesignMode.MATERIAL_3,
            UiDesignMode.MATERIAL_3_EXPRESSIVE.effective(expressiveAvailable = false)
        )
    }
}
