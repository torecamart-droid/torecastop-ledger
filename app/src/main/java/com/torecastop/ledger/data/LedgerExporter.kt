package com.torecastop.ledger.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the end-of-day export: a zip containing `sales.csv`, `trades.csv`
 * (when the session has trades — decision T4: separate file, same zip) and a
 * `photos/` folder with any captured images. Each item line is one CSV row,
 * grouped by `sale_id` / `trade_id` so multi-item transactions stay linked.
 * Timestamps are written in the device's local time zone. The zip is written
 * to `cacheDir/exports/` and returned as a shareable FileProvider Uri.
 */
object LedgerExporter {

    fun buildZip(
        context: Context,
        session: Session,
        sales: List<SaleWithItems>,
        trades: List<TradeWithItems>,
        cashAdjustments: List<CashAdjustment> = emptyList()
    ): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Prefer the show/event label for the filename, falling back to the date.
        val baseName = (session.label?.takeIf { it.isNotBlank() } ?: session.name)
        val safeName = baseName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        val zipFile = File(exportsDir, "torecastop_${safeName.ifEmpty { "session" }}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            zip.putNextEntry(ZipEntry("sales.csv"))
            zip.write(buildSalesCsv(sales).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (trades.isNotEmpty()) {
                zip.putNextEntry(ZipEntry("trades.csv"))
                zip.write(buildTradesCsv(trades).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // Cash reconciliation + adjustment log — only when there's something
            // to report (a counted float/drawer, or logged adjustments).
            val hasCashData = session.startingFloat != null ||
                session.countedCash != null ||
                cashAdjustments.isNotEmpty()
            if (hasCashData) {
                zip.putNextEntry(ZipEntry("cash.csv"))
                zip.write(buildCashCsv(session, sales, trades, cashAdjustments).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val photoPaths =
                sales.mapNotNull { it.sale.photoPath } +
                    trades.mapNotNull { it.trade.photoPath } +
                    listOfNotNull(session.cashCountPhotoPath)
            photoPaths.forEach { path ->
                val photo = File(path)
                if (!photo.exists()) return@forEach
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    /**
     * One file with two kinds of `row_type`: the `reconciliation` key figures
     * (float, expected vs counted, variance) and one `adjustment` row per logged
     * cash movement. Blank cells where a figure wasn't recorded.
     */
    private fun buildCashCsv(
        session: Session,
        sales: List<SaleWithItems>,
        trades: List<TradeWithItems>,
        adjustments: List<CashAdjustment>
    ): String {
        val cashSales = sales.sumOf { it.total }
        val tradeCash = trades.sumOf { it.cashReceived }
        val adjustmentsNet = adjustments.sumOf { it.amount }
        val expected = (session.startingFloat ?: 0.0) + cashSales + tradeCash + adjustmentsNet
        val variance = session.countedCash?.let { it - expected }

        val timestampFormat = timestampFormat()
        val sb = StringBuilder()
        sb.append("row_type,label_or_timestamp,amount,reason\n")
        fun recon(label: String, value: Double?) {
            sb.append("reconciliation,").append(label).append(',')
                .append(value?.let { money(it) } ?: "").append(",\n")
        }
        recon("starting_float", session.startingFloat)
        recon("cash_sales", cashSales)
        recon("trade_cash_net", tradeCash)
        recon("cash_adjustments_net", adjustmentsNet)
        recon("expected_cash", expected)
        recon("counted_cash", session.countedCash)
        recon("variance", variance)
        adjustments.forEach { adj ->
            sb.append("adjustment,")
                .append(csv(timestampFormat.format(Date(adj.timestamp)))).append(',')
                .append(money(adj.amount)).append(',')
                .append(csv(adj.reason)).append('\n')
        }
        return sb.toString()
    }

    private fun buildSalesCsv(sales: List<SaleWithItems>): String {
        val timestampFormat = timestampFormat()
        val sb = StringBuilder()
        sb.append("sale_id,timestamp,sku,quantity,unit_price,line_subtotal,item_note,note,photo\n")
        sales.forEach { saleWithItems ->
            val sale = saleWithItems.sale
            val timestamp = timestampFormat.format(Date(sale.timestamp))
            val photoName = sale.photoPath?.let { File(it).name } ?: ""
            saleWithItems.items.forEach { item ->
                sb.append(sale.id).append(',')
                sb.append(csv(timestamp)).append(',')
                sb.append(csv(item.sku)).append(',')
                sb.append(item.quantity).append(',')
                sb.append(money(item.price)).append(',')
                sb.append(money(item.subtotal)).append(',')
                sb.append(csv(item.note ?: "")).append(',')
                sb.append(csv(sale.note ?: "")).append(',')
                sb.append(csv(photoName)).append('\n')
            }
        }
        return sb.toString()
    }

    /**
     * One row per trade item; the trade-level fields (cash, value swing,
     * margin, headline value added, note, photo) repeat on each of its rows,
     * mirroring how sales.csv repeats note/photo.
     */
    private fun buildTradesCsv(trades: List<TradeWithItems>): String {
        val timestampFormat = timestampFormat()
        val sb = StringBuilder()
        sb.append(
            "trade_id,timestamp,direction,sku,card_name,quantity,unit_value,line_value," +
                "unit_cost_basis,item_note,cash_direction,cash_amount,value_swing,margin," +
                "value_added,note,photo\n"
        )
        trades.forEach { tradeWithItems ->
            val trade = tradeWithItems.trade
            val timestamp = timestampFormat.format(Date(trade.timestamp))
            val photoName = trade.photoPath?.let { File(it).name } ?: ""
            val marginText = tradeWithItems.margin?.let { money(it) } ?: ""
            tradeWithItems.items.forEach { item ->
                sb.append(trade.id).append(',')
                sb.append(csv(timestamp)).append(',')
                sb.append(csv(item.direction)).append(',')
                sb.append(csv(item.sku ?: "")).append(',')
                sb.append(csv(item.cardName ?: "")).append(',')
                sb.append(item.quantity).append(',')
                sb.append(money(item.tradeValue)).append(',')
                sb.append(money(item.lineValue)).append(',')
                sb.append(item.costBasis?.let { money(it) } ?: "").append(',')
                sb.append(csv(item.note ?: "")).append(',')
                sb.append(csv(trade.cashDirection)).append(',')
                sb.append(money(trade.cashAmount)).append(',')
                sb.append(money(tradeWithItems.valueSwing)).append(',')
                sb.append(marginText).append(',')
                sb.append(money(tradeWithItems.valueAdded)).append(',')
                sb.append(csv(trade.note ?: "")).append(',')
                sb.append(csv(photoName)).append('\n')
            }
        }
        return sb.toString()
    }

    /** Local-time timestamps (the device's zone), same in both CSVs. */
    private fun timestampFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** Quotes a CSV field and escapes embedded quotes per RFC 4180. */
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
