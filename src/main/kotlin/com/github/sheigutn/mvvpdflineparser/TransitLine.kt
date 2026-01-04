package com.github.sheigutn.mvvpdflineparser

data class TransitLine(
    val line: String,
    val backgroundColor: String,
    val textColor: String,
    val borderColor: String = "",
    var agencyId: String? = null,
    var agencyName: String? = null,
    val source: String
)