package com.artkashin.aiwalkingguide.museum

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream

object PhotoSanitizer {
    fun jpeg(context: Context,uri: Uri): ByteArray {
        val resolver=context.contentResolver
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        resolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,options)}
        require(options.outWidth>0 && options.outHeight>0)
        var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1024)sample*=2
        val bitmap=resolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}
            ?:throw IllegalArgumentException("Cannot decode photo")
        val orientation=try { resolver.openInputStream(uri).use{ExifInterface(it!!).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)} }catch(_: Exception){ExifInterface.ORIENTATION_NORMAL}
        val matrix=Matrix()
        when(orientation) {
            ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.postScale(-1f,1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.postScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{matrix.postRotate(90f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_TRANSVERSE->{matrix.postRotate(270f);matrix.postScale(-1f,1f)}
        }
        val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        return try { ByteArrayOutputStream().use{out->check(oriented.compress(Bitmap.CompressFormat.JPEG,82,out));out.toByteArray()} }
        finally { if(oriented!==bitmap)oriented.recycle();bitmap.recycle() }
    }
}
