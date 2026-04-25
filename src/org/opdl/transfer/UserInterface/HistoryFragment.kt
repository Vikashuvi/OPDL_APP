package org.opdl.transfer.UserInterface

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import org.opdl.transfer.Helpers.TransferHistoryHelper
import org.opdl.transfer.Helpers.TransferRecord
import org.opdl.transfer.UserInterface.compose.KdeTheme
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KdeTheme(requireContext()) {
                    HistoryScreen()
                }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val historyHelper = remember { TransferHistoryHelper(context) }
    val history = remember { mutableStateListOf<TransferRecord>() }
    
    LaunchedEffect(Unit) {
        history.clear()
        history.addAll(historyHelper.getHistory())
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transfer history yet",
                    color = Color(0xFF888888),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { record ->
                    HistoryItem(record)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(record: TransferRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (record.isUpload) Icons.Default.Send else Icons.Default.ArrowBack
            val iconColor = if (record.isUpload) Color.White else Color(0xFF4ADE80)

            Box(
                modifier = Modifier.size(40.dp).background(Color(0xFF2E2E2E), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.fileType} · ${record.fileSize} · ${record.duration}",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                Text(
                    text = record.source,
                    color = Color(0xFF555555),
                    fontSize = 11.sp
                )
            }
        }
    }
}
