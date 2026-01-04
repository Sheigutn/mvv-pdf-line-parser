package com.github.sheigutn.mvvpdflineparser

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.QuoteMode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.TextPosition
import java.awt.Color
import java.io.FileWriter


val positionMap = mutableMapOf<TextPosition, Color>()
val lineColors = mutableMapOf<String, Color>()

val busPrefixes = listOf("AÖ", "DGF", "VLK", "OVG")
val additionalBussesSuffixes = listOf("V", "W")

const val shortOperatorName = "mvv-regional-bus"
const val shape = "rectangle"

// MVV uses #878787 (135, 135, 135) for grey
private val ignoredColors = listOf(
    Color(135, 135, 135),
    Color.WHITE
)

fun main() {
    // Delfi GTFS (2025-12-29)
    val agencyParser = parseCSV("agency.txt")
    val routeParser = parseCSV("routes.txt")

    val allDelfiRoutes = routeParser.records
    val allDelfiAgencies = agencyParser.records

    val pdfDocument = Loader.loadPDF(
        ClassLoader.getSystemClassLoader().getResourceAsStream("2026_layout_MVV_Regio_0GESAMT.pdf")!!.readBytes()
    )
    // Trigger line name extraction
    MVVPDFTextStripper().getText(pdfDocument)

    var transitLines = mutableListOf<TransitLine>()

    lineColors.forEach {
        (line, color) ->
        transitLines += TransitLine(
            line,
            String.format("#%02x%02x%02x", color.red, color.green, color.blue),
            "#ffffff",
            "",
            source = "PDF",
        )
    }

    // Manually listed routes
    val manualRoutesParser = parseCSV("colors_manual.csv")
    val manualRoutes = manualRoutesParser.records
    manualRoutes.forEach {
        // Remove duplicate lines (applies to lines 323 and 978, since those are found in multiple PDFs and therefore also the manual colors CSV)
        transitLines.removeIf { transitLine -> transitLine.line == it["route_short_name"] }
        transitLines += TransitLine(
            it["route_short_name"],
            it["background_color"],
            it["text_color"],
            it["border_color"],
            source = "CSV"
        )
    }

    val mvvAgencies = allDelfiRoutes.filter {
        it["route_id"].matches(Regex(".*mvv.*\\|(Stadt|Regional|Express).*"))
    }.map { route ->
        allDelfiAgencies.first { agency ->
            agency["agency_id"] == route["agency_id"]
        }
    }.distinct()

    val mvvAgencyRoutes = allDelfiRoutes.filter {
            route -> mvvAgencies.any {
                agency -> agency["agency_id"] == route["agency_id"]
            }
    }.sortedBy {
        it["route_short_name"]
    }

    transitLines = transitLines.flatMap { transitLine ->
        val correctRoute = mvvAgencyRoutes.firstOrNull { route -> route["route_short_name"] == transitLine.line }

        correctRoute?.run {
            mvvAgencies.first {
                agency ->
                correctRoute["agency_id"] == agency["agency_id"]
            }
        }?.apply {
            transitLine.agencyId = this["agency_id"]
            transitLine.agencyName = this["agency_name"]
        }

        // Busses for additional service on a line have suffixes V or W
        return@flatMap listOf(transitLine, *additionalBussesSuffixes.mapNotNull { suffix ->
            return@mapNotNull if (mvvAgencyRoutes.any { route -> route["route_short_name"] == "${transitLine.line}${suffix}" }) {
                transitLine.copy(
                    line = "${transitLine.line}${suffix}"
                )
            } else null
        }.toTypedArray())
    }.sortedBy {
        transitLine ->
        if (transitLine.line[0].isLetter()) {
            // Push lines starting with a letter to the bottom
            return@sortedBy 10000
        }
        return@sortedBy transitLine.line.replace(Regex("\\D+"), "").toIntOrNull()
    }.toMutableList()

    val csvPrinter = CSVPrinter(
        FileWriter("mvv_colors.csv"),
        CSVFormat.DEFAULT.builder().setEscape('\\').setQuoteMode(QuoteMode.NONE).get()
    )

    csvPrinter.use {
        transitLines.filter { it.agencyId != null }.forEach {
                line -> createCSVLine(line, csvPrinter)
        }
    }

    transitLines.filter { it.agencyId == null }.forEach { line ->
        println("No MVV operated line found for route id ${line.line}. (Source: ${line.source})")
    }

    val mvvRoutesParser = parseCSV("mvv_routes.txt")
    val mvvRoutes = mvvRoutesParser.records

    mvvRoutes.filterNot {
        it["route_short_name"].startsWith("S")
    }.filter { transitLines.none { transitLine -> it["route_short_name"] == transitLine.line } }.forEach {
        println("Missing MVV line: " + it["route_short_name"])
    }

    println()
}

private fun parseCSV(resourceName: String): CSVParser {
    return CSVParser.parse(
        ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName),
        Charsets.UTF_8,
        CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setCommentMarker('#')
            .get()
    )
}

private fun createCSVLine(line: TransitLine, csvPrinter: CSVPrinter) {
    csvPrinter.printRecord(shortOperatorName, line.line, "", "", line.backgroundColor, line.textColor, line.borderColor, shape, "", line.agencyId.orEmpty(), line.agencyName.orEmpty())
}

private fun parseColor(text: String, textPositions: List<TextPosition>) {
    if (text.length in 3..4 || (busPrefixes.any { text.startsWith(it) }) || ((text.startsWith("(")) || text.endsWith(")"))) {
        val actualText = text.replace("(", "").replace(")", "")
        if (!lineColors.contains(actualText) &&
            " " !in actualText
            && (actualText.toIntOrNull() != null && actualText.toInt() > 200 // > 200 since all StadtBus lines are < 200
                    || (busPrefixes.any { actualText.startsWith(it)
            }))) {
            val color = positionMap[textPositions.first()]!!
            if (color !in ignoredColors) {
                lineColors[actualText] = color
            }
        }
    }
}

class MVVPDFTextStripper: PDFTextStripperSuper() {
    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (newLine && text.any { it.isDigit() }) {
            // Parsed text is something like 671533
            if (text.length == 6 && text.take(3).toIntOrNull() != null && text.substring(3, 6)
                    .toIntOrNull() != null
            ) {
                parseColor(text.take(3), textPositions.take(3))
                parseColor(text.substring(3, 6), textPositions.subList(3, 6))
            }
            // Parsed text is something like (678)(679)
            else if (text.length == 10) {
                parseColor(text.take(5), textPositions.take(5))
                parseColor(
                    text.substring(5, text.length),
                    textPositions.subList(5, text.length.coerceAtMost(textPositions.size))
                )
            }
            // Parsed text is something like (1041)(1041)
            else if (text.length == 12) {
                parseColor(text.take(6), textPositions.take(6))
                parseColor(
                    text.substring(6, text.length),
                    textPositions.subList(6, text.length.coerceAtMost(textPositions.size))
                )
            }
            // Parsed text is something like (2025/2026)
            else if (text.contains("/")) {
                val index = text.indexOf("/")
                parseColor(text.take(index), textPositions.take(index))
                parseColor(
                    text.substring(index, text.length).replace("/", ""), textPositions.subList(
                        index,
                        text.length.coerceAtMost(textPositions.size)
                    )
                )
            }
            // Otherwise just parse the text color
            else {
                parseColor(text, textPositions)
            }
        }
        super.writeString(text, textPositions)
        newLine = false
    }

    override fun processTextPosition(text: TextPosition) {
        super.processTextPosition(text)
        val color = Color(graphicsState.nonStrokingColor.toRGB())
        positionMap[text] = color
    }
}