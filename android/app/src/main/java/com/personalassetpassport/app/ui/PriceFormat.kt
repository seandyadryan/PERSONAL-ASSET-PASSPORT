package com.personalassetpassport.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Conventions for the currencies supported by the purchase-price selector.
fun usesDecimalComma(currency: String) = currency == "IDR" || currency == "EUR"

fun priceDecimal(currency: String) = if (usesDecimalComma(currency)) ',' else '.'

fun normalizePriceInput(value: String, currency: String): String? {
    val grouping = if (usesDecimalComma(currency)) '.' else ','
    val raw = value.replace(grouping.toString(), "")
    val decimal = Regex.escape(priceDecimal(currency).toString())
    return raw.takeIf { it.length <= 24 && Regex("[0-9]*($decimal[0-9]{0,2})?").matches(it) }
}

/** Adds grouping only to the display, keeping editing offsets and the stored amount intact. */
class PriceVisualTransformation(private val currency: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val decimal = priceDecimal(currency)
        val integerLength = raw.indexOf(decimal).takeIf { it >= 0 } ?: raw.length
        val positions = IntArray(raw.length + 1)
        val display = buildString {
            raw.forEachIndexed { index, character ->
                if (index > 0 && index < integerLength && (integerLength - index) % 3 == 0) {
                    append(if (usesDecimalComma(currency)) '.' else ',')
                }
                append(character)
                positions[index + 1] = length
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = positions[offset]
            override fun transformedToOriginal(offset: Int) =
                positions.indexOfLast { it <= offset }.coerceAtLeast(0)
        }
        return TransformedText(AnnotatedString(display), mapping)
    }
}
