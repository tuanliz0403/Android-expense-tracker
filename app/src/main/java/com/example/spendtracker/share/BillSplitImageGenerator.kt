package com.example.spendtracker.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import com.example.spendtracker.domain.model.BillSplitDetails
import com.example.spendtracker.ui.formatAud
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BillSplitImageGenerator {
    private const val WIDTH = 1440
    private const val PAD = 104f

    fun generate(split: BillSplitDetails, transactionTimestamp: Long): Bitmap {
        val height = (1120 + split.participants.size * 104 + split.lineItems.size * 82).coerceAtLeast(1480)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(238, 244, 240))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL) }
        canvas.drawRoundRect(RectF(48f, 48f, WIDTH - 48f, height - 48f), 48f, 48f, paint.apply { color = Color.WHITE })

        var y = 170f
        fun text(value: String, size: Float, color: Int = Color.rgb(30, 38, 34), bold: Boolean = false, gap: Float = size * 1.35f) {
            paint.color = color
            paint.typeface = android.graphics.Typeface.create("sans", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            var actualSize = size
            paint.textSize = actualSize
            val availableWidth = WIDTH - PAD * 2
            while (paint.measureText(value) > availableWidth && actualSize > 24f) {
                actualSize -= 2f
                paint.textSize = actualSize
            }
            val fitted = if (paint.measureText(value) <= availableWidth) value else {
                val count = paint.breakText(value, true, availableWidth - paint.measureText("…"), null)
                value.take(count.coerceAtLeast(1)) + "…"
            }
            canvas.drawText(fitted, PAD, y, paint)
            y += gap
        }
        fun divider() {
            y += 18f
            canvas.drawLine(PAD, y, WIDTH - PAD, y, paint.apply { color = Color.rgb(220, 228, 223); strokeWidth = 3f })
            y += 58f
        }

        text(split.title, 70f, bold = true, gap = 92f)
        text(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("en-AU")).format(Date(transactionTimestamp)), 34f, Color.DKGRAY, gap = 74f)
        text("Total: ${formatAud(split.totalCents)}", 42f, bold = true, gap = 72f)
        text("${split.participants.size} people", 34f, Color.DKGRAY, gap = 68f)
        text("${formatAud(split.perPersonCents)} each", 78f, Color.rgb(20, 112, 72), bold = true, gap = 104f)
        if (split.lineItems.isNotEmpty()) {
            text("Included transactions", 40f, bold = true, gap = 66f)
            split.lineItems.forEach { item -> text("• ${item.title} — ${formatAud(item.amountCents)}", 32f, Color.DKGRAY, gap = 62f) }
        }
        divider()
        val progress = when {
            split.isClosed -> "${split.paidCount} / ${split.participants.size} Paid — Closed ✓"
            split.isCompleted -> "${split.paidCount} / ${split.participants.size} Paid — Completed ✓"
            else -> "${split.paidCount} / ${split.participants.size} Paid"
        }
        text("Payment Progress", 40f, bold = true, gap = 62f)
        text(progress, 36f, if (split.isCompleted) Color.rgb(20, 125, 70) else Color.DKGRAY, bold = split.isCompleted, gap = 74f)
        split.participants.forEach { participant ->
            val marker = if (participant.isPaid) "✓" else "○"
            val detail = when {
                participant.isPaid -> "Paid"
                participant.isWaived -> "Covered by me"
                else -> "${formatAud(split.perPersonCents)} owing"
            }
            text("$marker  ${participant.name} — $detail", 34f,
                if (participant.isPaid) Color.rgb(21, 128, 70) else if (participant.isWaived) Color.rgb(63, 111, 143) else Color.rgb(160, 55, 55),
                bold = participant.isPaid, gap = 72f)
        }
        divider()
        text(if (split.isClosed) "Split closed — remaining balance covered by me" else "Please pay ${formatAud(split.perPersonCents)}", 50f, Color.rgb(20, 112, 72), bold = true, gap = 82f)
        text("PayID: ${split.payId}", 36f, bold = true, gap = 62f)
        text("Account Name: ${split.accountName}", 36f, gap = 76f)
        text("Please use the exact amount of ${formatAud(split.perPersonCents)}", 30f, Color.DKGRAY, gap = 48f)
        text("so Expense Tracker can automatically match your payment.", 30f, Color.DKGRAY)
        return bitmap
    }

    fun share(context: Context, bitmap: Bitmap) {
        val directory = File(context.cacheDir, "shared_bill_splits").apply { mkdirs() }
        val file = File(directory, "bill-split-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share bill split"))
    }
}
