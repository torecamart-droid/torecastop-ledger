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
 * Builds the end-of-day export: a zip containing `sales.csv` and a `photos/`
 * folder with any captured images. Each item line is one CSV row, grouped by
 * `sale_id` so multi-item transactions stay linked. The zip is written to
 * `cacheDir/exports/` and returned as a shareable FileProvider Uri.
 */
object LedgerExporter {

    fun buildZip(context: Context, session: Session, sales: List<SaleWithItems>): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = session.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        val zipFile = File(exportsDir, "torecastop_${safeName.ifEmpty { "session" }}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            zip.putNextEntry(ZipEntry("sales.csv"))
            zip.write(buildCsv(sales).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            sales.forEach { saleWithItems ->
                val path = saleWithItems.sale.photoPath ?: return@forEach
                val photo = File(path)
                if (!photo.exists()) return@forEach
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private fun buildCsv(sales: List<SaleWithItems>): String {
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("sale_id,timestamp,sku,quantity,unit_price,line_subtotal,note,photo\n")
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
                sb.append(csv(sale.note ?: "")).append(',')
                sb.append(csv(photoName)).append('\n')
            }
        }
        return sb.toString()
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** Quotes a CSV field and escapes embedded quotes per RFC 4180. */
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
