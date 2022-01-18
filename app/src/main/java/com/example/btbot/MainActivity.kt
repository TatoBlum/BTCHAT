package com.example.btbot


import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.btbot.databinding.ActivityMainBinding
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val uuid: UUID =
        UUID.fromString("06ae0a74-7bd4-43aa-ab5d-2511f3f6bab1") // GENERATE NEW UUID IF IT WONT WORK
    private lateinit var mySelectedBluetoothDevice: BluetoothDevice
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var socket: BluetoothSocket
    private lateinit var myHandler: Handler


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter.isEnabled == false) {
            bluetoothAdapter.enable()
        }

        val bondedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        bondedDevices.forEach { device ->
            if (device.address == "00:00:00:00:00:00") { // CHANGE BLUETOOTH ADDRESS HERE
                mySelectedBluetoothDevice = device
                binding.bluetoothAddressTextView.text = mySelectedBluetoothDevice.address
            } else if (device.address == "00:00:00:00:00:00") { // CHANGE BLUETOOTH ADDRESS HERE
                mySelectedBluetoothDevice = device
                binding.bluetoothNameTextView.text = mySelectedBluetoothDevice.name
                binding.bluetoothAddressTextView.text = mySelectedBluetoothDevice.address
            }
        }
        AcceptThread().start()
        myHandler = Handler()

        binding.connectToDeviceButton.setOnClickListener() {
            ConnectThread(mySelectedBluetoothDevice).start()
        }
        binding.disconnectButton.setOnClickListener() {
            Log.d("Other phone", "Closing socket and connection")
            socket.close()
            binding.connectedOrNotTextView.text = "Not connected"
            binding.connectToDeviceButton.isEnabled = true; binding.disconnectButton.isEnabled =
            false; binding.sendMessageButton.isEnabled = false
        }


        binding.sendMessageButton.setOnClickListener() {
            if (binding.writeMessageEditText.length() > 0) {
                val connectThreadInstance = ConnectThread(mySelectedBluetoothDevice)
                connectThreadInstance.writeMessage(
                   // binding.writeMessageEditText.getText().toString()
                    binding.writeMessageEditText.text.toString()
                )
                return@setOnClickListener
            } else {
                Toast.makeText(applicationContext, "Empty message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }
    }

    private inner class ConnectThread(device: BluetoothDevice): Thread() {
        private var newSocket = device.createRfcommSocketToServiceRecord(uuid)

        override fun run() {
            try {
                Log.d("You", "Connecting socket")
                myHandler.post {
                    binding.connectedOrNotTextView.text = "Connecting..."
                    binding.connectToDeviceButton.isEnabled = false
                }
                socket = newSocket
                socket.connect()
                Log.d("You", "Socket connected")
                myHandler.post {
                    binding.connectedOrNotTextView.text = "Connected"
                    binding.connectToDeviceButton.isEnabled = false; binding.disconnectButton.isEnabled = true;
                    binding.sendMessageButton.isEnabled = true
                }
            }catch (e1: Exception){
                Log.e("You", "Error connecting socket, " + e1)
                myHandler.post {
                    binding.connectedOrNotTextView.text = "Connection failed"
                    binding.connectToDeviceButton.isEnabled = true; binding.disconnectButton.isEnabled = false;
                    binding.sendMessageButton.isEnabled = false
                }
            }
        }
        fun writeMessage(newMessage: String){
            Log.d("You", "Sending")
            val outputStream = socket.outputStream
            try {
                outputStream.write(newMessage.toByteArray())
                outputStream.flush()
                Log.d("You", "Sent " + newMessage)
                myHandler.post {
                    binding.receivedMessageUserTextView.text = "Me: "
                    binding.receivedMessageTextView.text = newMessage
                }
            } catch (e: Exception) {
                Log.e("You", "Cannot send, " + e)
                return
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        override fun run() {
            val inputStream = socket.inputStream
            val buffer = ByteArray(1024)
            var bytes = 0
            while (true) {
                try {
                    bytes = inputStream.read(buffer, bytes, 1024 - bytes)
                    val receivedMessage = String(buffer).substring(0, bytes)

                    Log.d("Other phone", "New received message: " + receivedMessage)
                    myHandler.post {
                        binding.receivedMessageUserTextView.text = mySelectedBluetoothDevice.name + ": "
                        binding.receivedMessageTextView.text = receivedMessage
                    }
                    bytes = 0
                } catch (e :IOException) {
                    e.printStackTrace()
                    Log.d("Other phone", "Error reading")
                    break
                }
            }
        }
    }

    private inner class AcceptThread() : Thread() {
        private var cancelled: Boolean
        private val serverSocket: BluetoothServerSocket?

        init {
            if (bluetoothAdapter.isEnabled) {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("test", uuid)
                cancelled = false
            } else {
                serverSocket = null
                cancelled = true
            }

        }
        override fun run() {
            var socket: BluetoothSocket
            while(true) {
                if (cancelled) {
                    break
                }
                try {
                    socket = serverSocket!!.accept()
                } catch(e: IOException) {
                    break
                }
                if (!cancelled && socket != null) {
                    Log.d("Other phone", "Connecting")
                    ConnectedThread(socket).start()
                }
            }
        }

        fun cancel() {
            cancelled = true
            serverSocket!!.close()
        }
    }

}

