package com.hubble.networktest.app

import android.content.Context
import kotlinx.android.synthetic.activity_main.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.common.io.BaseEncoding
import com.hubble.actors.Actor
import com.hubble.sdk.proto.Event
import com.hubble.sdk.proto.Header
import com.hubble.sdk.proto.ValueType
import com.squareup.wire.Message
import com.squareup.wire.Wire
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

val address = "52.32.190.38"
val port = 41234

class WireFrame<M : Message> {
	var message:M? = null

	constructor(message:M) {
		this.message = message
	}

	fun toByteArray(): ByteArray {
		val header = Header(this.message!!.serializedSize)
		val messageBytes = this.message!!.toByteArray()
		return header.toByteArray() + messageBytes
	}

	companion object {
		const val HEADER_SIZE = 2
		val wire = Wire()
		inline fun <reified M: Message> parseFrom(bytes: ByteArray, klazz: Class<M>): WireFrame<M> {
			val headerBytes: ByteArray = bytes.sliceArray(0..HEADER_SIZE-1)
			Log.d("network test", "length: ${headerBytes.size}")
			val header = wire.parseFrom(headerBytes, Header::class.java)
			val messageBytes: ByteArray = bytes.sliceArray(HEADER_SIZE..HEADER_SIZE+header.length-1)
			val message: M = wire.parseFrom(messageBytes, M::class.java)
			return WireFrame(message)
		}
	}
}

class MainActivity : AppCompatActivity() {

	object networkActor : Actor() {
		val socket:DatagramSocket? = null
			get() {
				if(field == null) {
					field = DatagramSocket()
				}
				return field
			}

		var sequence:Long = 0

		override fun receive(m: Any?): Any? {
			when (m) {
				is RecvMessage -> {
					Log.d("network test", "receiving message from ${address}:${port}")
					try {
						val bytes = ByteArray(1024)
						val packet = DatagramPacket(bytes, bytes.size)
						socket!!.receive(packet)
						Log.d("network test", "received WireFrame: ${BaseEncoding.base16().encode(bytes)}")
						val frame = WireFrame.parseFrom(bytes, Event::class.java)
						val newState = frame.message!!.newState.string_value
						val oldState = frame.message!!.oldState.string_value
						Log.d("network test", "WireFrame(timestamp: ${frame.message!!.timestamp} size: ${frame.message!!.serializedSize}, newState:${newState}, oldState:${oldState})")
					} catch (t:Throwable) {
						Log.e("network test", Log.getStackTraceString(t))
					}
					return null
				}
				is SendMessage -> {
					try {
						Log.d("network test", "Sending message to ${address}:${port}")
						var event = Event(
								"deviceId",
								"peripheralId",
								"profileId",
								ValueType("one", null),
								ValueType("two", null),
								0,
								sequence++)
						val frame = WireFrame(event)
						var bytes = frame.toByteArray()
						Log.d("network test", "Sending (length: ${bytes.size}) -> ${BaseEncoding.base16().encode(bytes)}")
						socket!!.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(address), port))
					} catch (e:Throwable) {
						Log.e("network test", Log.getStackTraceString(e))
					}
					return null
				}
				else -> return null
			}
		}
	}

	class SendMessage()
	class RecvMessage()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		sendButton.setOnClickListener {
			Log.d("network test", "sendButton clicked")
			networkActor.send(SendMessage())
		}
		receiveButton.setOnClickListener {
			Log.d("network test", "receiveButton clicked")
			networkActor.send(RecvMessage())
		}
	}


	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		val id = item.itemId

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true
		}

		return super.onOptionsItemSelected(item)
	}
}
