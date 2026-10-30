package com.artkashin.aiwalkingguide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.museum.PhotoSanitizer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoSanitizerTest {
    @Test fun removesMetadataResizesAndLeavesGalleryOriginalUntouched() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=File(context.cacheDir,"fixture-photo.jpg")
        val bitmap=Bitmap.createBitmap(1300,2400,Bitmap.Config.ARGB_8888)
        original.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)};bitmap.recycle()
        ExifInterface(original.path).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE,"55/1,45/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N")
            setAttribute(ExifInterface.TAG_USER_COMMENT,"sensitive-fixture-comment")
            setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()
        }
        val before=original.readBytes()
        val clean=PhotoSanitizer.jpeg(context,Uri.fromFile(original))
        val exif=ExifInterface(clean.inputStream())
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE));assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        val decoded=BitmapFactory.decodeByteArray(clean,0,clean.size)
        assertTrue(decoded.width<=1024 && decoded.height<=1024);assertTrue(decoded.width>decoded.height);decoded.recycle()
        assertArrayEquals(before,original.readBytes());original.delete()
    }
}
