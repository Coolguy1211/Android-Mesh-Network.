package com.zaiah.meshapp

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.zaiah.meshapp.databinding.ActivityChatBinding
import com.zaiah.meshapp.network.models.ChatMessage
import com.zaiah.meshapp.network.models.MeshPacket
import java.nio.charset.Charset

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val meshApp get() = MeshApp.instance
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ChatAdapter(meshApp.chatMessages)
        binding.rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChat.adapter = adapter

        binding.btnSendChat.setOnClickListener {
            val text = binding.inputChat.text.toString()
            if (text.isNotBlank()) {
                val msg = ChatMessage(
                    senderId = meshApp.localNodeId,
                    message = text,
                    isSentByMe = true
                )
                meshApp.chatMessages.add(msg)
                adapter.notifyItemInserted(meshApp.chatMessages.size - 1)
                binding.rvChat.scrollToPosition(meshApp.chatMessages.size - 1)
                
                meshApp.meshManager.sendToNode(
                    "BROADCAST",
                    text.toByteArray(Charset.forName("UTF-8")),
                    MeshPacket.PacketType.TEXT
                )
                binding.inputChat.text.clear()
            }
        }

        meshApp.chatListener = {
            runOnUiThread {
                adapter.notifyItemInserted(meshApp.chatMessages.size - 1)
                binding.rvChat.scrollToPosition(meshApp.chatMessages.size - 1)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        meshApp.chatListener = null
        super.onDestroy()
    }
}
