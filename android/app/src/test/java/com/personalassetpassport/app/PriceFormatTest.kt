package com.personalassetpassport.app

import androidx.compose.ui.text.AnnotatedString
import com.personalassetpassport.app.data.parseMinor
import com.personalassetpassport.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class PriceFormatTest {
    @Test fun groupsWhilePreservingFractionsAndEditOffsets() {
        for ((currency, raw, expected) in listOf(
            Triple("IDR", "1234567,50", "1.234.567,50"),
            Triple("EUR", "1234567,50", "1.234.567,50"),
            Triple("USD", "1234567.50", "1,234,567.50"),
            Triple("JPY", "1234567", "1,234,567"),
            Triple("IDR", "", ""),
            Triple("USD", "1234.", "1,234."),
        )) {
            val result = PriceVisualTransformation(currency).filter(AnnotatedString(raw))
            assertEquals(expected, result.text.text)
            for (offset in 0..raw.length) {
                assertEquals(offset, result.offsetMapping.transformedToOriginal(result.offsetMapping.originalToTransformed(offset)))
            }
            for (offset in 0..expected.length) {
                assertTrue(result.offsetMapping.transformedToOriginal(offset) in 0..raw.length)
            }
        }
    }

    @Test fun pastedPricesAndCurrencyChangesPreserveExactAmount() {
        val rupiah = normalizePriceInput("1.234.567,50", "IDR")!!
        assertEquals("1234567,50", rupiah)
        val dollars = rupiah.replace(priceDecimal("IDR"), priceDecimal("USD"))
        assertEquals(123456750L, parseMinor(dollars, usesDecimalComma("USD")))
        assertEquals(123456750L, parseMinor(rupiah, usesDecimalComma("IDR")))
        assertEquals("1234567.50", normalizePriceInput("1,234,567.50", "USD"))
        assertNull(normalizePriceInput("-100", "IDR"))
        assertNull(normalizePriceInput("12,345", "IDR"))
    }
}
