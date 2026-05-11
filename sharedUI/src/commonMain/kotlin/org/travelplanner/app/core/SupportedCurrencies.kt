package org.travelplanner.app.core

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String,
)

val SUPPORTED_CURRENCIES: List<CurrencyOption> =
    listOf(
        CurrencyOption("RUB", "₽", "Российский рубль"),
        CurrencyOption("USD", "$", "Доллар США"),
        CurrencyOption("EUR", "€", "Евро"),
        CurrencyOption("JPY", "¥", "Японская иена"),
        CurrencyOption("GBP", "£", "Фунт стерлингов"),
        CurrencyOption("CNY", "¥", "Китайский юань"),
        CurrencyOption("KRW", "₩", "Южнокорейская вона"),
        CurrencyOption("THB", "฿", "Тайский бат"),
        CurrencyOption("TRY", "₺", "Турецкая лира"),
    )

fun currencySymbol(code: String): String =
    SUPPORTED_CURRENCIES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.symbol
        ?: code
