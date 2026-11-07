package com.example.samcam

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaItem(val uri: Uri, val video: Boolean, val name: String)

private fun queryOne(ctx: Context, base: Uri, video: Boolean, into: MutableList<Pair<Long, MediaItem>>) {
    try {
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DISPLAY_NAME)
        val sel = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?" else null
        val args = if (Build.VERSION.SDK_INT >= 29) arrayOf("%SamCam%") else null
        ctx.contentResolver.query(base, proj, sel, args, MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { c ->
            var n = 0
            while (c.moveToNext() && n < 300) {
                val id = c.getLong(0)
                into.add(Pair(c.getLong(1), MediaItem(ContentUris.withAppendedId(base, id), video, c.getString(2) ?: "")))
                n++
            }
        }
    } catch (e: Exception) {
    }
}

fun queryMedia(ctx: Context): List<MediaItem> {
    val l = ArrayList<Pair<Long, MediaItem>>()
    queryOne(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, l)
    queryOne(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, l)
    l.sortByDescending { it.first }
    return l.map { it.second }
}

fun loadFull(ctx: Context, uri: Uri, video: Boolean): Bitmap? {
    try {
        if (video) {
            return if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(1080, 1080), null) else null
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val big = maxOf(info.size.width, info.size.height)
                decoder.setTargetSampleSize(maxOf(1, big / 1800))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
    } catch (e: Throwable) {
    }
    return null
}

fun openInPhotos(ctx: Context, uri: Uri, video: Boolean) {
    val mime = if (video) "video/*" else "image/*"
    val base = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent(base).setPackage("com.google.android.apps.photos"))
    } catch (e: Exception) {
        try {
            ctx.startActivity(base)
        } catch (x: Exception) {
            Toast.makeText(ctx, "No viewer app found", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun GalleryViewer(start: Uri?, startVideo: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var startIdx by remember { mutableIntStateOf(-1) }
    var tick by remember { mutableIntStateOf(0) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) tick = tick + 1
    }
    LaunchedEffect(tick) {
        val r = withContext(Dispatchers.IO) { queryMedia(ctx) }
        var l: List<MediaItem> = r
        if (start != null && l.none { it.uri == start }) l = listOf(MediaItem(start, startVideo, "")) + l
        items = l
        if (startIdx < 0) startIdx = l.indexOfFirst { it.uri == start }.coerceAtLeast(0)
    }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { }) {
        val list = items
        if (list == null || startIdx < 0) {
            Text("Loading…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (list.isEmpty()) {
            Text("Nothing here yet", color = Color.White, modifier = Modifier.align(Alignment.Center))
            Text("‹ Back", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.systemBarsPadding().padding(16.dp).clickable { onClose() })
        } else {
            val pager = rememberPagerState(initialPage = startIdx.coerceIn(0, list.size - 1)) { list.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val item = list[page]
                val bmp by produceState<Bitmap?>(null, item.uri) {
                    value = withContext(Dispatchers.IO) { loadFull(ctx, item.uri, item.video) }
                }
                Box(
                    Modifier.fillMaxSize().clickable { if (item.video) openInPhotos(ctx, item.uri, true) },
                    contentAlignment = Alignment.Center
                ) {
                    val b = bmp
                    if (b != null) {
                        Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    if (item.video) Text("▶", color = Color.White, fontSize = 64.sp)
                }
            }
            val cur = list[pager.currentPage.coerceIn(0, list.size - 1)]
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().systemBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("‹", color = Color.White, fontSize = 30.sp, modifier = Modifier.clickable { onClose() }.padding(horizontal = 8.dp))
                Text("${pager.currentPage + 1} / ${list.size}", color = Color.White, fontSize = 15.sp)
                Text("", color = Color.White)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("Share", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = if (cur.video) "video/*" else "image/*"
                    i.putExtra(Intent.EXTRA_STREAM, cur.uri)
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "Share"))
                }.padding(12.dp))
                Text("Photos", color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.clickable { openInPhotos(ctx, cur.uri, cur.video) }.padding(12.dp))
                Text("Delete", color = Color(0xFFFF6B6B), fontSize = 16.sp, modifier = Modifier.clickable {
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val ps = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(cur.uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(ps.intentSender).build())
                        } else {
                            ctx.contentResolver.delete(cur.uri, null, null)
                            tick = tick + 1
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Can't delete this item", Toast.LENGTH_SHORT).show()
                    }
                }.padding(12.dp))
            }
        }
    }
}
