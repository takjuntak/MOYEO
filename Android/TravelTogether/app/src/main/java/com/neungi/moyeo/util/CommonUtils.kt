package com.neungi.moyeo.util

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.TextView
import androidx.palette.graphics.Palette
import com.naver.maps.geometry.LatLng
import com.neungi.domain.model.PhotoEntity
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CommonUtils {

    private val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$".toRegex()
    private val passwordRegex =
        "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()\\-_=+{}\\[\\]|;:'\",.<>?/]).{8,16}$".toRegex()
    private val phoneNumberRegex = "^010-\\d{4}-\\d{4}\$".toRegex()

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0

        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    private fun uriToBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun validateEmail(email: CharSequence): Boolean = emailRegex.matches(email)

    fun validatePassword(password: CharSequence): Boolean = passwordRegex.matches(password)

    fun validatePhoneNumber(phoneNumber: CharSequence): Boolean =
        phoneNumberRegex.matches(phoneNumber)

    fun convertToDegree(value: String): Double {
        val dms = value.split(",", limit = 3)
        val degrees = dms[0].split("/").let { it[0].toDouble() / it[1].toDouble() }
        val minutes = dms[1].split("/").let { it[0].toDouble() / it[1].toDouble() }
        val seconds = dms[2].split("/").let { it[0].toDouble() / it[1].toDouble() }

        return degrees + (minutes / 60) + (seconds / 3600)
    }

    fun convertToYYYYMMDDwithHyphen(date: LocalDate?): String =
        (date ?: LocalDate.now()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    fun convertToyyyyMMdd(date: LocalDate?): String =
        (date ?: LocalDate.now()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    fun formatDateTime(input: String): String {
        if (input == "") return "1970.01.01"
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

        val dateTime = LocalDateTime.parse(input, inputFormatter)
        return dateTime.format(outputFormatter)
    }

    fun formatLongToDateTime(timestamp: Long): String {
        val formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneId.of("Asia/Seoul"))
                .withZone(ZoneId.systemDefault())

        return formatter.format(Instant.ofEpochMilli(timestamp))
    }

    fun formatLongToyyyyMMddWithDot(timestamp: Long): String {
        val formatter =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))
                .withZone(ZoneId.systemDefault())

        return formatter.format(Instant.ofEpochMilli(timestamp))
    }

    fun formatZonedDateTimeWithZone(zonedDateTime: ZonedDateTime,pattern:String ="yyyy.MM.dd"): String {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Seoul")).format(formatter)
    }

    fun drawableToBitmap(drawable: Drawable, size: Int = 144): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                    val tempCanvas = Canvas(it)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(tempCanvas)
                },
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
            )
        }

        val radius = size / 2F
        canvas.drawCircle(radius, radius, radius, paint)

        return bitmap
    }

    fun adjustTextColorFromBackgroundUri(context: Context, uri: Uri, textView: TextView) {
        val bitmap = uriToBitmap(context.contentResolver, uri) ?: return

        Palette.from(bitmap).generate { palette ->
            val dominantColor = palette?.getDominantColor(Color.WHITE) ?: Color.WHITE
            val luminance = calculateLuminance(dominantColor)

            textView.setTextColor(if (luminance < 0.4) Color.WHITE else Color.BLACK)
        }
    }

    fun initPlaceNumber(places: List<String>): Int {
        var number = 1
        places.sortedBy { it }.forEach { place ->
            if (place == "장소 $number") {
                number++
            }
        }

        return number
    }

    fun isPhotoTakenInKorea(latitude: Double, longitude: Double): Boolean {
        return latitude in 33.0..38.6 && longitude in 124.6..131.9
    }

    fun calculateLocation(photos: List<PhotoEntity>): LatLng {
        var averageLatitude = photos.filter { it.latitude != 0.0 }.map { it.latitude }.average()
        if (averageLatitude.isNaN()) {
            averageLatitude = 0.0
        }
        var averageLongitude = photos.filter { it.longitude != 0.0 }.map { it.longitude }.average()
        if (averageLongitude.isNaN()) {
            averageLongitude = 0.0
        }

        Timber.d("$averageLatitude, $averageLongitude")

        return LatLng(averageLatitude, averageLongitude)
    }

    fun getDdayText(startDate: ZonedDateTime, endDate: ZonedDateTime): String {
        val today = ZonedDateTime.now()

        // 시간 정보를 제외하고 날짜만 비교하기 위해 날짜 부분만 추출
        val todayDate = today.toLocalDate()
        val startLocalDate = startDate.toLocalDate()
        val endLocalDate = endDate.toLocalDate()

        return when {
            // 시작일이 오늘보다 미래인 경우: D-N 형식
            todayDate.isBefore(startLocalDate) -> {
                val daysUntilStart = ChronoUnit.DAYS.between(todayDate, startLocalDate)
                "D-$daysUntilStart"
            }
            // 오늘이 여행 기간 내인 경우: Today
            todayDate.isEqual(startLocalDate) || (todayDate.isAfter(startLocalDate) && todayDate.isBefore(endLocalDate) || todayDate.isEqual(endLocalDate)) -> {
                "Today"
            }
            // 이미 종료된 여행인 경우: 종료된 날짜 표시
            else -> {
                val daysSinceEnd = ChronoUnit.DAYS.between(endLocalDate, todayDate)
                "종료된 여행"
            }
        }
    }
}